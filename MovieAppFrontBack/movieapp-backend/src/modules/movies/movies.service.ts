// File: src/modules/movies/movies.service.ts
// AGGIORNATO: gestione completa con UserMovieEntity

import { Injectable, Logger } from '@nestjs/common';
import { DatabaseService } from '../../database/database.service';
import { TmdbService } from '../tmdb/tmdb.service';
import { WebsocketGateway } from '../websocket/websocket.gateway';
import { UserMoviesService, ImportCounters } from './user-movies.service';
import { MovieStatus } from '../../database/entities/user-movie.entity';
import { v4 as uuidv4 } from 'uuid';
import {
  Movie,
  EnrichmentResult,
  BatchUploadResult,
} from '../../common/interfaces/movie.interface';

@Injectable()
export class MoviesService {
  private readonly logger = new Logger(MoviesService.name);

  constructor(
    private readonly databaseService: DatabaseService,
    private readonly tmdbService: TmdbService,
    private readonly websocketGateway: WebsocketGateway,
    private readonly userMoviesService: UserMoviesService,
  ) {}

  async batchUploadWithUserAssociation(
    userId: string,
    watchlist: Movie[],
    watched: Movie[],
  ): Promise<BatchUploadResult & { importCounters: ImportCounters }> {
    const sessionId = uuidv4();
    const startTime = Date.now();

    this.logger.log(`📦 === BATCH UPLOAD SESSION ${sessionId} ===`);
    this.logger.log(`User ID: ${userId}`);
    this.logger.log(`watchlist: ${watchlist.length} film`);
    this.logger.log(`watched: ${watched.length} film`);

    try {
      // STEP 1: Salva tutti i film nella tabella movies
      this.logger.log(`💾 salvataggio film in tabella movies...`);
      const allMovies = [...watchlist, ...watched];
      await this.databaseService.saveMovies(allMovies);
      this.logger.log(`✅ salvati ${allMovies.length} film in tabella movies`);

      // ✅ Notifica inizio enrichment
      await this.websocketGateway.notifyEnrichmentStarted(sessionId, allMovies.length);

      // STEP 2: Enrichment watchlist CON PROGRESS
      this.logger.log(`🎬 enrichment watchlist...`);
      const watchlistEnrichment = await this.tmdbService.enrichMovies(watchlist, {
        onProgress: async (processed, total, currentMovie) => {
          // ✅ Invia progress via WebSocket
          await this.websocketGateway.notifyEnrichmentProgress(
            sessionId,
            processed,
            total,
            currentMovie
          );
        }
      });
      
      const watchlistResult: EnrichmentResult = {
        sessionId: uuidv4(),
        successfulMovies: watchlistEnrichment.successfulMovies,
        failedMovies: watchlistEnrichment.failedMovies,
        totalProcessed: watchlistEnrichment.totalProcessed,
        successRate: watchlistEnrichment.successRate,
        cacheHits: 0,
      };

      // STEP 3: Enrichment watched CON PROGRESS
      this.logger.log(`🎬 enrichment watched...`);
      const watchedEnrichment = await this.tmdbService.enrichMovies(watched, {
        onProgress: async (processed, total, currentMovie) => {
          // ✅ Invia progress via WebSocket (offset per watchlist)
          await this.websocketGateway.notifyEnrichmentProgress(
            sessionId,
            watchlist.length + processed,
            allMovies.length,
            currentMovie
          );
        }
      });
      
      const watchedResult: EnrichmentResult = {
        sessionId: uuidv4(),
        successfulMovies: watchedEnrichment.successfulMovies,
        failedMovies: watchedEnrichment.failedMovies,
        totalProcessed: watchedEnrichment.totalProcessed,
        successRate: watchedEnrichment.successRate,
        cacheHits: 0,
      };

      // STEP 4: Associazioni user_movies
      this.logger.log(`🔗 creazione associazioni user_movies...`);
      
      const watchlistAssociations = watchlistResult.successfulMovies.map(m => ({
        movieId: m.id,
        status: MovieStatus.WATCHLIST,
      }));

      const watchedAssociations = watchedResult.successfulMovies.map(m => ({
        movieId: m.id,
        status: MovieStatus.WATCHED,
      }));

      await this.userMoviesService.batchAssociateMovies(
        userId,
        [...watchlistAssociations, ...watchedAssociations]
      );

      this.logger.log(`✅ associazioni create per ${watchlistAssociations.length + watchedAssociations.length} film`);

      // STEP 5: Recupera contatori aggiornati
      const userStats = await this.userMoviesService.getUserMovieStats(userId);

      const importCounters: ImportCounters = {
        watchedFromFile: watched.length,
        watchlistFromFile: watchlist.length,
        totalWatched: userStats.watchedCount,
        totalWatchlist: userStats.watchlistCount,
      };

      // Statistiche finali
      const totalMovies = watchlist.length + watched.length;
      const totalEnriched =
        watchlistResult.successfulMovies.filter((m) => m.tmdb_id).length +
        watchedResult.successfulMovies.filter((m) => m.tmdb_id).length;
      const totalCacheHits = watchlistResult.cacheHits + watchedResult.cacheHits;

      // ✅ Notifica completamento
      await this.websocketGateway.notifyEnrichmentCompleted(
        sessionId,
        totalMovies,
        totalEnriched,
        totalCacheHits
      );

      const duration = Date.now() - startTime;

      this.logger.log(`=== BATCH UPLOAD COMPLETATO ===`);
      this.logger.log(`durata: ${(duration / 1000).toFixed(1)}s`);
      this.logger.log(`film arricchiti: ${totalEnriched}/${totalMovies}`);

      return {
        sessionId,
        watchlistResult,
        watchedResult,
        importCounters,
        summary: {
          totalMovies,
          watchlistCount: watchlist.length,
          watchedCount: watched.length,
          totalEnriched,
          overallSuccessRate: totalEnriched / totalMovies,
          cacheHitsTotal: totalCacheHits,
        },
      };
    } catch (error) {
      this.logger.error(`❌ errore batch upload: ${error.message}`);
      
      // ✅ Notifica errore
      await this.websocketGateway.notifyEnrichmentError(sessionId, error.message);
      
      throw error;
    }
  }

  /**
   * 🆕 Recupera film di un utente specifico
   */
  async getUserMovies(
    userId: string,
    filters?: {
      status?: 'watched' | 'watchlist';
      query?: string;
      genre?: string;
      year?: number;
      director?: string;
      minRating?: number;
      maxRating?: number;
      sortBy?: string;
      sortOrder?: 'ASC' | 'DESC';
      limit?: number;
      offset?: number;
    },
  ): Promise<{ movies: Movie[]; total: number }> {
    try {
      this.logger.debug(`🔍 Recupero film per utente ${userId}`);

      // Converti status in MovieStatus enum
      const movieStatus = filters?.status === 'watched' 
        ? MovieStatus.WATCHED 
        : filters?.status === 'watchlist'
        ? MovieStatus.WATCHLIST
        : undefined;

      // Recupera film dell'utente tramite UserMoviesService
      const userMoviesData = await this.userMoviesService.getUserMovies(
        userId,
        movieStatus,
      );

      let movies = userMoviesData;

      // Applica filtri aggiuntivi
      if (filters?.query) {
        const queryLower = filters.query.toLowerCase();
        movies = movies.filter(m => 
          m.title.toLowerCase().includes(queryLower) ||
          m.director?.toLowerCase().includes(queryLower)
        );
      }

      if (filters?.genre) {
        movies = movies.filter(m =>
          m.genres?.some(g => g.toLowerCase().includes(filters.genre!.toLowerCase()))
        );
      }

      if (filters?.year) {
        movies = movies.filter(m => m.year === filters.year);
      }

      if (filters?.director) {
        movies = movies.filter(m =>
          m.director?.toLowerCase().includes(filters.director!.toLowerCase())
        );
      }

      const total = movies.length;

      // Ordinamento
      if (filters?.sortBy) {
        movies = this.sortMovies(movies, filters.sortBy, filters.sortOrder);
      }

      // Paginazione
      const offset = filters?.offset || 0;
      const limit = filters?.limit || 50;
      movies = movies.slice(offset, offset + limit);

      this.logger.debug(`✅ trovati ${total} film (mostrati ${movies.length})`);

      return { movies, total };
    } catch (error) {
      this.logger.error(`errore getUserMovies: ${error.message}`);
      return { movies: [], total: 0 };
    }
  }

  /**
   * 🆕 Statistiche utente
   */
  async getUserStats(userId: string): Promise<any> {
    try {
      const userStats = await this.userMoviesService.getUserMovieStats(userId);
      
      return {
        user_id: userId,
        total_movies: userStats.totalMovies,
        watched_count: userStats.watchedCount,
        watchlist_count: userStats.watchlistCount,
        average_rating: userStats.averageRating,
      };
    } catch (error) {
      this.logger.error(`errore getUserStats: ${error.message}`);
      return { error: error.message };
    }
  }

  /**
   * Enrichment batch
   */
  async enrichMovies(movies: Movie[]): Promise<EnrichmentResult> {
    try {
      const result = await this.tmdbService.enrichMovies(movies);
      
      // TmdbService ritorna un risultato senza sessionId e cacheHits
      // Li aggiungiamo qui per conformità con EnrichmentResult
      return {
        sessionId: uuidv4(),
        successfulMovies: result.successfulMovies,
        failedMovies: result.failedMovies,
        totalProcessed: result.totalProcessed,
        successRate: result.successRate,
        cacheHits: 0, // TmdbService non traccia cache hits
      };
    } catch (error) {
      this.logger.error(`errore enrichMovies: ${error.message}`);
      throw error;
    }
  }

  /**
   * Cerca film
   */
  async searchMovies(filters: {
    query?: string;
    genre?: string;
    year?: number;
    director?: string;
    sortBy?: string;
    sortOrder?: 'ASC' | 'DESC';
    limit?: number;
    offset?: number;
  }): Promise<{ movies: Movie[]; total: number }> {
    try {
      let movies = await this.databaseService.getAllMovies();

      // Applica filtri
      if (filters.query) {
        const queryLower = filters.query.toLowerCase();
        movies = movies.filter(m =>
          m.title.toLowerCase().includes(queryLower) ||
          m.director?.toLowerCase().includes(queryLower)
        );
      }

      if (filters.genre) {
        movies = movies.filter(m =>
          m.genres?.some(g => g.toLowerCase().includes(filters.genre!.toLowerCase()))
        );
      }

      if (filters.year) {
        movies = movies.filter(m => m.year === filters.year);
      }

      if (filters.director) {
        movies = movies.filter(m =>
          m.director?.toLowerCase().includes(filters.director!.toLowerCase())
        );
      }

      const total = movies.length;

      // Ordinamento
      if (filters.sortBy) {
        movies = this.sortMovies(movies, filters.sortBy, filters.sortOrder);
      }

      // Paginazione
      const offset = filters.offset || 0;
      const limit = filters.limit || 50;
      movies = movies.slice(offset, offset + limit);

      return { movies, total };
    } catch (error) {
      this.logger.error(`errore searchMovies: ${error.message}`);
      return { movies: [], total: 0 };
    }
  }

  /**
   * Ordinamento film
   */
  private sortMovies(
    movies: Movie[],
    sortBy: string,
    sortOrder?: 'ASC' | 'DESC',
  ): Movie[] {
    const order = sortOrder || 'ASC';

    const sorted = [...movies].sort((a, b) => {
      let comparison = 0;

      switch (sortBy) {
        case 'title':
          comparison = a.title.localeCompare(b.title);
          break;
        case 'year':
          comparison = (a.year || 0) - (b.year || 0);
          break;
        case 'director':
          comparison = (a.director || '').localeCompare(b.director || '');
          break;
        case 'rating':
          comparison = (a.tmdb_rating || 0) - (b.tmdb_rating || 0);
          break;
        case 'runtime':
          comparison = (a.runtime || 0) - (b.runtime || 0);
          break;
        default:
          comparison = 0;
      }

      return order === 'DESC' ? -comparison : comparison;
    });

    return sorted;
  }

  /**
   * Health check
   */
  async healthCheck(): Promise<{ status: string; timestamp: string }> {
    return {
      status: 'ok',
      timestamp: new Date().toISOString(),
    };
  }

  /**
   * Statistiche sistema
   */
  async getStats(): Promise<any> {
    try {
      const allMovies = await this.databaseService.getAllMovies();
      const enrichedMovies = allMovies.filter(m => m.tmdb_id);

      return {
        totalMovies: allMovies.length,
        enrichedMovies: enrichedMovies.length,
        enrichmentRate: allMovies.length > 0
          ? (enrichedMovies.length / allMovies.length) * 100
          : 0,
      };
    } catch (error) {
      this.logger.error(`errore getStats: ${error.message}`);
      return { error: error.message };
    }
  }

  /**
   * Get all movies (legacy)
   */
  async getAllMovies(): Promise<Movie[]> {
    try {
      return await this.databaseService.getAllMovies();
    } catch (error) {
      this.logger.error(`errore getAllMovies: ${error.message}`);
      return [];
    }
  }

  /**
   * Get movie by ID
   */
  async getMovieById(id: string): Promise<Movie | null> {
    try {
      return await this.databaseService.getMovieById(id);
    } catch (error) {
      this.logger.error(`errore getMovieById: ${error.message}`);
      return null;
    }
  }

  /**
   * Delete all movies
   */
  async deleteAllMovies(): Promise<void> {
    try {
      const allMovies = await this.databaseService.getAllMovies();
      for (const movie of allMovies) {
        await this.databaseService.deleteMovie(movie.id);
      }
      this.logger.log('🗑️ tutti i film eliminati');
    } catch (error) {
      this.logger.error(`errore deleteAllMovies: ${error.message}`);
      throw error;
    }
  }
}