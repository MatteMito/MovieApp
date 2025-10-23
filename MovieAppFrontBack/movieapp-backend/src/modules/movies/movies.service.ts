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
    private readonly userMoviesService: UserMoviesService, // 🆕 AGGIUNTO
  ) {}

  /**
   * 🆕 NUOVA FUNZIONE: batch upload con associazione user_movies
   * Questa è la versione modificata che gestisce correttamente:
   * 1. I contatori dal file i  mportato (watchedFromFile, watchlistFromFile)
   * 2. I contatori totali dopo refresh (totalWatched, totalWatchlist)
   */
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
      // STEP 1: salva tutti i film nella tabella movies (se non esistono già)
      this.logger.log(`💾 salvataggio film in tabella movies...`);

      const allMovies = [...watchlist, ...watched];
      
      // Rimuovi il campo user_id dai film prima di salvarli in movies
      const moviesWithoutUserId = allMovies.map(movie => {
        const { user_id, ...movieWithoutUser } = movie as any;
        return movieWithoutUser;
      });
      
      await this.databaseService.saveMovies(moviesWithoutUserId);

      this.logger.log(`✅ salvati ${moviesWithoutUserId.length} film in tabella movies`);

      // STEP 2: enrichment intelligente watchlist
      this.logger.log(`🎬 enrichment watchlist...`);
      const watchlistResult = await this.enrichMovies(watchlist);

      // STEP 3: enrichment intelligente watched
      this.logger.log(`🎬 enrichment watched...`);
      const watchedResult = await this.enrichMovies(watched);

      // STEP 4: 🆕 Associa i film all'utente nella tabella user_movies
      this.logger.log(`🔗 associazione film all'utente nella tabella user_movies...`);

      // Prepara i dati per watchlist
      const watchlistAssociations = watchlistResult.successfulMovies.map((movie) => ({
        movieId: movie.id,
        status: MovieStatus.WATCHLIST,
        source: movie.source || 'UNKNOWN',
        userRating: movie.user_rating,
        watchedDate: undefined,
        userReview: movie.user_review,
      }));

      // Prepara i dati per watched
      const watchedAssociations = watchedResult.successfulMovies.map((movie) => ({
        movieId: movie.id,
        status: MovieStatus.WATCHED,
        source: movie.source || 'UNKNOWN',
        userRating: movie.user_rating,
        watchedDate: movie.watched_date ? new Date(movie.watched_date) : undefined,
        userReview: movie.user_review,
      }));

      // Salva le associazioni in batch
      const watchlistBatchResult = await this.userMoviesService.batchAssociateMoviesToUser(
        userId,
        watchlistAssociations,
      );

      const watchedBatchResult = await this.userMoviesService.batchAssociateMoviesToUser(
        userId,
        watchedAssociations,
      );

      this.logger.log(`✅ associazioni completate:`);
      this.logger.log(`   watchlist: ${watchlistBatchResult.created} creati, ${watchlistBatchResult.updated} aggiornati`);
      this.logger.log(`   watched: ${watchedBatchResult.created} creati, ${watchedBatchResult.updated} aggiornati`);

      // STEP 5: 🆕 Recupera i contatori aggiornati
      const userStats = await this.userMoviesService.getUserMovieStats(userId);

      // Costruisci l'oggetto contatori
      const importCounters: ImportCounters = {
        // Contatori dal file appena importato
        watchedFromFile: watched.length,
        watchlistFromFile: watchlist.length,
        // Contatori totali (disponibili solo dopo refresh)
        totalWatched: userStats.watchedCount,
        totalWatchlist: userStats.watchlistCount,
      };

      this.logger.log(`📊 Contatori importazione:`);
      this.logger.log(`   Dal file: ${importCounters.watchedFromFile} watched, ${importCounters.watchlistFromFile} watchlist`);
      this.logger.log(`   Totali: ${importCounters.totalWatched} watched, ${importCounters.totalWatchlist} watchlist`);

      // calcola statistiche finali
      const totalMovies = watchlist.length + watched.length;
      const totalEnriched =
        watchlistResult.successfulMovies.filter((m) => m.tmdb_id).length +
        watchedResult.successfulMovies.filter((m) => m.tmdb_id).length;
      const totalCacheHits = watchlistResult.cacheHits + watchedResult.cacheHits;

      const duration = Date.now() - startTime;

      this.logger.log(`=== BATCH UPLOAD COMPLETATO ===`);
      this.logger.log(`durata: ${(duration / 1000).toFixed(1)}s`);
      this.logger.log(`film totali: ${totalMovies}`);
      this.logger.log(`film arricchiti: ${totalEnriched}`);
      this.logger.log(`cache hits: ${totalCacheHits}`);
      this.logger.log(
        `success rate: ${((totalEnriched / totalMovies) * 100).toFixed(1)}%`,
      );

      const result: BatchUploadResult & { importCounters: ImportCounters } = {
        sessionId,
        watchlistResult,
        watchedResult,
        importCounters, // 🆕 AGGIUNTO
        summary: {
          totalMovies,
          watchlistCount: watchlist.length,
          watchedCount: watched.length,
          totalEnriched,
          overallSuccessRate: totalEnriched / totalMovies,
          cacheHitsTotal: totalCacheHits,
        },
      };

      return result;
    } catch (error) {
      this.logger.error(`❌ errore batch upload session ${sessionId}: ${error.message}`);
      throw error;
    }
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
   * Get all movies (usato da controller legacy)
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
   * Get unenriched movies
   */
  async getUnenrichedMovies(): Promise<Movie[]> {
    try {
      return await this.databaseService.getUnenrichedMovies();
    } catch (error) {
      this.logger.error(`errore getUnenrichedMovies: ${error.message}`);
      return [];
    }
  }

  /**
   * Delete all movies
   */
  async deleteAllMovies(): Promise<void> {
    try {
      await this.databaseService.deleteAllMovies();
      this.logger.log('🗑️ tutti i film eliminati');
    } catch (error) {
      this.logger.error(`errore deleteAllMovies: ${error.message}`);
      throw error;
    }
  }

  /**
   * 🆕 NUOVA FUNZIONE: recupera film filtrati per utente
   * Ritorna solo i film che appartengono all'utente specificato
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

      // Step 1: Recupera i movie_id dell'utente
      const movieStatus = filters?.status === 'watched' 
        ? MovieStatus.WATCHED 
        : filters?.status === 'watchlist'
        ? MovieStatus.WATCHLIST
        : undefined;

      const userMovieIds = await this.userMoviesService.getUserMovieIds(userId, movieStatus);

      if (userMovieIds.length === 0) {
        this.logger.debug(`ℹ️ Nessun film trovato per utente ${userId}`);
        return { movies: [], total: 0 };
      }

      // Step 2: Recupera i film completi dal database
      let movies = await this.databaseService.getMoviesByIds(userMovieIds);

      // Step 3: Applica filtri aggiuntivi
      if (filters?.query) {
        const query = filters.query.toLowerCase();
        movies = movies.filter(
          (m) =>
            m.title.toLowerCase().includes(query) ||
            m.director?.toLowerCase().includes(query) ||
            m.overview?.toLowerCase().includes(query),
        );
      }

      if (filters?.genre) {
        movies = movies.filter((m) =>
          m.genres?.some((g) => g.toLowerCase() === filters.genre.toLowerCase()),
        );
      }

      if (filters?.year) {
        movies = movies.filter((m) => m.year === filters.year);
      }

      if (filters?.director) {
        const director = filters.director.toLowerCase();
        movies = movies.filter((m) => m.director?.toLowerCase().includes(director));
      }

      if (filters?.minRating) {
        movies = movies.filter(
          (m) =>
            (m.user_rating && m.user_rating >= filters.minRating) ||
            (m.tmdb_rating && m.tmdb_rating >= filters.minRating),
        );
      }

      if (filters?.maxRating) {
        movies = movies.filter(
          (m) =>
            (m.user_rating && m.user_rating <= filters.maxRating) ||
            (m.tmdb_rating && m.tmdb_rating <= filters.maxRating),
        );
      }

      const total = movies.length;

      // Step 4: Ordinamento
      if (filters?.sortBy) {
        movies = this.sortMovies(movies, filters.sortBy, filters.sortOrder);
      }

      // Step 5: Paginazione
      const offset = filters?.offset || 0;
      const limit = filters?.limit || 50;
      movies = movies.slice(offset, offset + limit);

      this.logger.debug(
        `🔍 ricerca: trovati ${total} film per utente ${userId} (mostrati ${movies.length})`,
      );

      return { movies, total };
    } catch (error) {
      this.logger.error(`errore recupero film utente: ${error.message}`);
      return { movies: [], total: 0 };
    }
  }

  /**
   * 🆕 NUOVA FUNZIONE: statistiche utente
   */
  async getUserStats(userId: string): Promise<any> {
    try {
      const userStats = await this.userMoviesService.getUserMovieStats(userId);
      
      this.logger.debug(`📊 statistiche utente ${userId}:`, userStats);

      return {
        user_id: userId,
        total_movies: userStats.totalMovies,
        watched_count: userStats.watchedCount,
        watchlist_count: userStats.watchlistCount,
        average_rating: userStats.averageRating,
        last_import: userStats.lastImportDate,
      };
    } catch (error) {
      this.logger.error(`errore recupero statistiche utente: ${error.message}`);
      return { error: error.message };
    }
  }

  /**
   * enrichMovies - arricchimento intelligente dei film
   * Gestisce cache, ricerca TMDB e WebSocket notifications
   */
  async enrichMovies(movies: Movie[]): Promise<EnrichmentResult> {
    const startTime = Date.now();
    const successfulMovies: Movie[] = [];
    const failedMovies: Movie[] = [];
    let cacheHits = 0;
    let tmdbCalls = 0;

    this.logger.debug(`🎬 enrichMovies: inizio arricchimento ${movies.length} film`);

    for (const movie of movies) {
      try {
        // Se il film ha già tmdb_id, verifica se è in cache
        if (movie.tmdb_id) {
          const cached = movie.tmdb_id
          ? await this.databaseService.getMovieByTmdbId(movie.tmdb_id)
          : null;
          if (cached) {
            cacheHits++;
            this.logger.debug(`💰 cache hit per ${movie.title}`);
            successfulMovies.push({ ...movie, ...cached });
            continue;
          }
        }

        // Cerca su TMDB
        const tmdbData = await this.tmdbService.enrichMovie(movie);
        tmdbCalls++;

        if (tmdbData) {
          this.logger.debug(`✅ trovato su TMDB: ${movie.title}`);
          const enriched = { ...movie, ...tmdbData };
          successfulMovies.push(enriched);
        } else {
          this.logger.warn(`❌ non trovato su TMDB: ${movie.title}`);
          failedMovies.push(movie);
        }
      } catch (error) {
        this.logger.error(
          `errore arricchimento ${movie.title}: ${error.message}`,
        );
        failedMovies.push(movie);
      }
    }

    const duration = Date.now() - startTime;
    const successRate = successfulMovies.length / movies.length;

    this.logger.log(`✅ enrichMovies completato:`);
    this.logger.log(`   successi: ${successfulMovies.length}`);
    this.logger.log(`   falliti: ${failedMovies.length}`);
    this.logger.log(`   cache hits: ${cacheHits}`);
    this.logger.log(`   TMDB calls: ${tmdbCalls}`);
    this.logger.log(`   success rate: ${(successRate * 100).toFixed(1)}%`);
    this.logger.log(`   durata: ${(duration / 1000).toFixed(1)}s`);

    const sessionId = `enrich_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
    const totalProcessed = successfulMovies.length + failedMovies.length;

    return {
      sessionId,
      successfulMovies,
      failedMovies: failedMovies.map((movie) => ({
        movie: {
          id: movie.id,
          title: movie.title,
          year: movie.year,
          user_rating: movie.user_rating,
          watched_date: movie.watched_date,
          user_review: movie.user_review,
          is_watched: movie.is_watched,
          source: movie.source,
        },
        error: 'Enrichment failed',
      })),
      totalProcessed,
      successRate,
      cacheHits,
    };
  }

  /**
   * ordina film per campo specificato
   */
  private sortMovies(
    movies: Movie[],
    sortBy: string,
    sortOrder: 'ASC' | 'DESC' = 'ASC',
  ): Movie[] {
    const sorted = [...movies].sort((a, b) => {
      let comparison = 0;

      switch (sortBy) {
        case 'title':
          comparison = a.title.localeCompare(b.title);
          break;
        case 'year':
          comparison = (a.year || 0) - (b.year || 0);
          break;
        case 'rating':
          const ratingA = a.user_rating || a.tmdb_rating || 0;
          const ratingB = b.user_rating || b.tmdb_rating || 0;
          comparison = ratingA - ratingB;
          break;
        case 'popularity':
          comparison = (a.popularity || 0) - (b.popularity || 0);
          break;
        default:
          comparison = 0;
      }

      return sortOrder === 'ASC' ? comparison : -comparison;
    });

    return sorted;
  }

  /**
   * VECCHIO METODO - Mantenuto per retrocompatibilità
   * Usa batchUploadWithUserAssociation invece
   */
  async batchUpload(
    watchlist: Movie[],
    watched: Movie[],
  ): Promise<BatchUploadResult> {
    const sessionId = uuidv4();
    const startTime = Date.now();

    this.logger.log(`📦 === BATCH UPLOAD SESSION ${sessionId} ===`);
    this.logger.log(`watchlist: ${watchlist.length} film`);
    this.logger.log(`watched: ${watched.length} film`);

    try {
      // salva tutti i film nel database
      this.logger.log(`💾 salvataggio film in database...`);
      const allMovies = [...watchlist, ...watched];
      await this.databaseService.saveMovies(allMovies);
      this.logger.log(`✅ salvati ${allMovies.length} film`);

      // enrichment intelligente watchlist
      this.logger.log(`🎬 enrichment watchlist...`);
      const watchlistResult = await this.enrichMovies(watchlist);

      // enrichment intelligente watched
      this.logger.log(`🎬 enrichment watched...`);
      const watchedResult = await this.enrichMovies(watched);

      // calcola statistiche finali
      const totalMovies = watchlist.length + watched.length;
      const totalEnriched =
        watchlistResult.successfulMovies.filter((m) => m.tmdb_id).length +
        watchedResult.successfulMovies.filter((m) => m.tmdb_id).length;
      const totalCacheHits = watchlistResult.cacheHits + watchedResult.cacheHits;

      const duration = Date.now() - startTime;

      this.logger.log(`=== BATCH UPLOAD COMPLETATO ===`);
      this.logger.log(`durata: ${(duration / 1000).toFixed(1)}s`);
      this.logger.log(`film totali: ${totalMovies}`);
      this.logger.log(`film arricchiti: ${totalEnriched}`);
      this.logger.log(`cache hits: ${totalCacheHits}`);
      this.logger.log(
        `success rate: ${((totalEnriched / totalMovies) * 100).toFixed(1)}%`,
      );

      return {
        sessionId,
        watchlistResult,
        watchedResult,
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
      this.logger.error(
        `❌ errore batch upload session ${sessionId}: ${error.message}`,
      );
      throw error;
    }
  }

  /**
   * cerca film per titolo/query
   */
  async searchMovies(
    filters: {
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
      const query = filters.query || '';
      this.logger.debug(`🔍 ricerca film: "${query}"`);

      let movies = await this.databaseService.getAllMovies();

      if (query) {
        const lowerQuery = query.toLowerCase();
        movies = movies.filter(
          (m) =>
            m.title.toLowerCase().includes(lowerQuery) ||
            m.director?.toLowerCase().includes(lowerQuery) ||
            m.overview?.toLowerCase().includes(lowerQuery),
        );
      }

      // applica filtri aggiuntivi
      if (filters?.genre) {
        movies = movies.filter((m) =>
          m.genres?.some(
            (g) => g.toLowerCase() === filters.genre.toLowerCase(),
          ),
        );
      }

      if (filters?.year) {
        movies = movies.filter((m) => m.year === filters.year);
      }

      if (filters?.director) {
        const director = filters.director.toLowerCase();
        movies = movies.filter((m) =>
          m.director?.toLowerCase().includes(director),
        );
      }

      if (filters?.minRating) {
        movies = movies.filter(
          (m) =>
            (m.user_rating && m.user_rating >= filters.minRating) ||
            (m.tmdb_rating && m.tmdb_rating >= filters.minRating),
        );
      }

      if (filters?.maxRating) {
        movies = movies.filter(
          (m) =>
            (m.user_rating && m.user_rating <= filters.maxRating) ||
            (m.tmdb_rating && m.tmdb_rating <= filters.maxRating),
        );
      }

      const total = movies.length;

      // ordinamento
      if (filters?.sortBy) {
        movies = this.sortMovies(movies, filters.sortBy, filters.sortOrder);
      }

      // paginazione
      const offset = filters?.offset || 0;
      const limit = filters?.limit || 50;
      movies = movies.slice(offset, offset + limit);

      this.logger.debug(
        `🔍 ricerca: trovati ${total} film (mostrati ${movies.length})`,
      );

      return { movies, total };
    } catch (error) {
      this.logger.error(`errore ricerca film: ${error.message}`);
      return { movies: [], total: 0 };
    }
  }

  /**
   * recupera un singolo film per ID
   */
  async getMovieById(id: string): Promise<Movie | null> {
    try {
      return await this.databaseService.getMovieById(id);
    } catch (error) {
      this.logger.error(`errore recupero film ${id}: ${error.message}`);
      return null;
    }
  }

  /**
   * aggiorna un film esistente
   */
  async updateMovie(id: string, updates: Partial<Movie>): Promise<Movie> {
    try {
      this.logger.debug(`🔄 aggiornamento film ${id}`);
      const updated = await this.databaseService.updateMovie(id, updates);
      this.logger.debug(`✅ film ${id} aggiornato`);
      return updated;
    } catch (error) {
      this.logger.error(`errore aggiornamento film ${id}: ${error.message}`);
      throw error;
    }
  }

  /**
   * elimina un film
   */
  async deleteMovie(id: string): Promise<void> {
    try {
      this.logger.debug(`🗑️ eliminazione film ${id}`);
      await this.databaseService.deleteMovie(id);
      this.logger.debug(`✅ film ${id} eliminato`);
    } catch (error) {
      this.logger.error(`errore eliminazione film ${id}: ${error.message}`);
      throw error;
    }
  }

  /**
   * statistiche generali
   */
  async getStats(): Promise<any> {
    try {
      const movies = await this.databaseService.getAllMovies();
      const watchlistCount = movies.filter((m) => m.status === 'watchlist').length;
      const watchedCount = movies.filter((m) => m.status === 'watched').length;

      const totalRatings = movies.reduce(
        (sum, m) => sum + (m.user_rating || 0),
        0,
      );
      const ratedMovies = movies.filter((m) => m.user_rating).length;
      const averageRating = ratedMovies > 0 ? totalRatings / ratedMovies : 0;

      return {
        total_movies: movies.length,
        watchlist_count: watchlistCount,
        watched_count: watchedCount,
        average_rating: averageRating,
        genres: this.extractGenres(movies),
      };
    } catch (error) {
      this.logger.error(`errore recupero statistiche: ${error.message}`);
      return { error: error.message };
    }
  }

  /**
   * estrae lista generi unici
   */
  private extractGenres(movies: Movie[]): string[] {
    const genres = new Set<string>();
    movies.forEach((m) => {
      if (m.genres) {
        m.genres.forEach((g) => genres.add(g));
      }
    });
    return Array.from(genres).sort();
  }
}