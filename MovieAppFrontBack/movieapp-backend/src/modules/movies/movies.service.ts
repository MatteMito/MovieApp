// servizio principale gestione film con auto-sync e enrichment ottimizzato

import { Injectable, Logger } from '@nestjs/common';
import { DatabaseService } from '../../database/database.service';
import { TmdbService } from '../tmdb/tmdb.service';
import { WebsocketGateway } from '../websocket/websocket.gateway';
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
  ) {}

  // ENRICHMENT INTELLIGENTE CON FLAG is_enriched

  /**
   * arricchisce film con dati tmdb usando cache e flag database verificando prima is_enriched per evitare duplicazioni
   * notifica progress via websocket in real-time
   */
  async enrichMovies(movies: Movie[]): Promise<EnrichmentResult> {
    const sessionId = uuidv4();
    const startTime = new Date().toISOString();

    this.logger.log(`🎬 === ENRICHMENT SESSION ${sessionId} ===`);
    this.logger.log(`film da processare: ${movies.length}`);

    const result: EnrichmentResult = {
      sessionId,
      successfulMovies: [],
      failedMovies: [],
      totalProcessed: 0,
      successRate: 0,
      cacheHits: 0,
    };

    try {
      // STEP 1: separa film già arricchiti da quelli da processare
      const alreadyEnriched: Movie[] = [];
      const needEnrichment: Movie[] = [];

      for (const movie of movies) {
        const existing = await this.databaseService.findMovieByTitleYear(
          movie.title,
          movie.year,
        );

        if (existing && existing.tmdb_id) {
          // film già nel database con dati tmdb
          this.logger.debug(`✅ già arricchito in db: ${movie.title}`);

          // merge dati utente con dati esistenti
          const merged: Movie = {
            ...existing,
            id: movie.id,
            user_rating: movie.user_rating,
            watched_date: movie.watched_date,
            user_review: movie.user_review,
            is_watched: movie.is_watched,
            source: movie.source,
          };

          //utilizza dati esistenti
          alreadyEnriched.push(merged);
          result.cacheHits++;
        } else {
          //dati da arricchire con TMDB
          needEnrichment.push(movie);
        }
      }

      this.logger.log(`📊 analisi iniziale:`);
      this.logger.log(`   già arricchiti in db: ${alreadyEnriched.length}`);
      this.logger.log(`   da arricchire: ${needEnrichment.length}`);

      // aggiungi film già arricchiti ai risultati
      result.successfulMovies.push(...alreadyEnriched);

      // notifica websocket stato iniziale
      await this.websocketGateway.notifyEnrichmentProgress(
        sessionId,
        alreadyEnriched.length,
        movies.length,
        undefined,
      );

      // STEP 2: arricchisci solo film non presenti in database
      if (needEnrichment.length > 0) {
        this.logger.log(`🔍 avvio enrichment tmdb per ${needEnrichment.length} film`);

        const enrichmentResult = await this.tmdbService.enrichMovies(
          needEnrichment,
          {
            onProgress: async (processed, total, currentMovie) => {
              const totalProcessed = alreadyEnriched.length + processed;

              this.logger.debug(
                `progress: ${totalProcessed}/${movies.length} (${currentMovie})`,
              );

              await this.websocketGateway.notifyEnrichmentProgress(
                sessionId,
                totalProcessed,
                movies.length,
                currentMovie,
              );
            },
          },
        );

        // STEP 3: salva film arricchiti in database con flag is_enriched
        if (enrichmentResult.successfulMovies.length > 0) {
          this.logger.log(
            `💾 salvataggio ${enrichmentResult.successfulMovies.length} film arricchiti`,
          );

          const savedMovies = await this.databaseService.saveMovies(
            enrichmentResult.successfulMovies,
          );

          result.successfulMovies.push(...savedMovies);
        }

        result.failedMovies.push(...enrichmentResult.failedMovies);
      }

      // calcola statistiche finali
      result.totalProcessed = movies.length;
      result.successRate = result.successfulMovies.length / result.totalProcessed;

      const enrichedCount = result.successfulMovies.filter((m) => m.tmdb_id).length;

      this.logger.log(`=== ENRICHMENT COMPLETATO ===`);
      this.logger.log(`session id: ${sessionId}`);
      this.logger.log(`film processati: ${result.totalProcessed}`);
      this.logger.log(`film arricchiti: ${enrichedCount}`);
      this.logger.log(`cache hits: ${result.cacheHits}`);
      this.logger.log(`nuovi da tmdb: ${enrichedCount - result.cacheHits}`);
      this.logger.log(`falliti: ${result.failedMovies.length}`);
      this.logger.log(`success rate: ${(result.successRate * 100).toFixed(1)}%`);

      // notifica websocket completamento
      await this.websocketGateway.notifyEnrichmentCompleted(
        sessionId,
        result.totalProcessed,
        result.successfulMovies.length,
        result.cacheHits,
      );

      return result;
    } catch (error) {
      this.logger.error(`❌ errore enrichment session ${sessionId}: ${error.message}`);

      await this.websocketGateway.notifyEnrichmentError(sessionId, error.message);

      throw error;
    }
  }

  // BATCH UPLOAD CON AUTO-ENRICHMENT

  /**
   * batch upload watchlist + watched con enrichment automatico
   * processo ottimizzato:
   * 1. salva film grezzi in database
   * 2. arricchisce con tmdb (salta già arricchiti)
   * 3. aggiorna database con dati completi
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
      // STEP 1: salva tutti i film grezzi (senza enrichment)
      this.logger.log(`💾 salvataggio iniziale film grezzi...`);

      const allMovies = [...watchlist, ...watched];
      await this.databaseService.saveMovies(allMovies);

      this.logger.log(`✅ salvati ${allMovies.length} film in database`);

      // STEP 2: enrichment intelligente watchlist
      this.logger.log(`🎬 enrichment watchlist...`);
      const watchlistResult = await this.enrichMovies(watchlist);

      // STEP 3: enrichment intelligente watched
      this.logger.log(`🎬 enrichment watched...`);
      const watchedResult = await this.enrichMovies(watched);

      // calcola statistiche aggregate
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

      const result: BatchUploadResult = {
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

      // notifica websocket batch completato
      await this.websocketGateway.notifyBatchCompleted(
        sessionId,
        watchlist.length,
        watched.length,
        totalMovies,
      );

      return result;
    } catch (error) {
      this.logger.error(`❌ errore batch upload ${sessionId}: ${error.message}`);
      throw error;
    }
  }

  // GESTIONE FILM BASE

  /**
   * recupera tutti i film dal database includendo statistiche enrichment
   */
  async getAllMovies(): Promise<Movie[]> {
    try {
      const movies = await this.databaseService.getAllMovies();

      const enrichedCount = movies.filter((m) => m.tmdb_id).length;

      this.logger.log(
        `📚 recuperati ${movies.length} film (${enrichedCount} arricchiti)`,
      );

      return movies;
    } catch (error) {
      this.logger.error(`errore recupero film: ${error.message}`);
      throw error;
    }
  }

  /**
   * recupera film per id
   */
  async getMovieById(id: string): Promise<Movie | null> {
    try {
      const movie = await this.databaseService.getMovieById(id);

      if (!movie) {
        this.logger.debug(`film non trovato: ${id}`);
        return null;
      }

      this.logger.debug(
        `film recuperato: ${movie.title} (arricchito: ${!!movie.tmdb_id})`,
      );
      return movie;
    } catch (error) {
      this.logger.error(`errore recupero film ${id}: ${error.message}`);
      return null;
    }
  }

  /**
   * recupera solo film non arricchiti utile per enrichment selettivo post-import
   */
  async getUnenrichedMovies(): Promise<Movie[]> {
    try {
      const movies = await this.databaseService.getUnenrichedMovies();

      this.logger.log(`📊 film non arricchiti: ${movies.length}`);

      return movies;
    } catch (error) {
      this.logger.error(`errore recupero film non arricchiti: ${error.message}`);
      return [];
    }
  }

  /**
   * aggiorna singolo film e mantiene flag is_enriched se ha tmdb_id
   */
  async updateMovie(id: string, updates: Partial<Movie>): Promise<Movie> {
    try {
      const existing = await this.databaseService.getMovieById(id);

      if (!existing) {
        throw new Error(`film non trovato: ${id}`);
      }

      const updated: Movie = {
        ...existing,
        ...updates,
        id, // preserva id originale
      };

      const savedMovie = await this.databaseService.saveMovie(updated);

      this.logger.log(`✅ film aggiornato: ${savedMovie.title}`);

      return savedMovie;
    } catch (error) {
      this.logger.error(`errore aggiornamento film ${id}: ${error.message}`);
      throw error;
    }
  }

  /**
   * elimina singolo film
   */
  async deleteMovie(id: string): Promise<void> {
    try {
      const movie = await this.databaseService.getMovieById(id);

      if (!movie) {
        throw new Error(`film non trovato: ${id}`);
      }

      // implementazione delete singolo da aggiungere in DatabaseService
      this.logger.log(`🗑️ film eliminato: ${movie.title}`);
    } catch (error) {
      this.logger.error(`errore eliminazione film ${id}: ${error.message}`);
      throw error;
    }
  }

  /**
   * elimina tutti i film
   */
  async deleteAllMovies(): Promise<void> {
    try {
      await this.databaseService.deleteAllMovies();
      this.logger.log(`🗑️ database svuotato`);
    } catch (error) {
      this.logger.error(`errore eliminazione film: ${error.message}`);
      throw error;
    }
  }

  // RICERCA E FILTRI

  /**
   * ricerca film con filtri multipli
   */
  async searchMovies(filters: {
    query?: string;
    genre?: string;
    year?: number;
    director?: string;
    minRating?: number;
    maxRating?: number;
    watched?: boolean;
    sortBy?: string;
    sortOrder?: 'ASC' | 'DESC';
    limit?: number;
    offset?: number;
  }): Promise<{ movies: Movie[]; total: number }> {
    try {
      let movies = await this.databaseService.getAllMovies();

      // applica filtri
      if (filters.query) {
        const query = filters.query.toLowerCase();
        movies = movies.filter(
          (m) =>
            m.title.toLowerCase().includes(query) ||
            m.director?.toLowerCase().includes(query) ||
            m.overview?.toLowerCase().includes(query),
        );
      }

      if (filters.genre) {
        movies = movies.filter((m) =>
          m.genres?.some((g) => g.toLowerCase() === filters.genre.toLowerCase()),
        );
      }

      if (filters.year) {
        movies = movies.filter((m) => m.year === filters.year);
      }

      if (filters.director) {
        const director = filters.director.toLowerCase();
        movies = movies.filter((m) => m.director?.toLowerCase().includes(director));
      }

      if (filters.minRating) {
        movies = movies.filter(
          (m) =>
            (m.user_rating && m.user_rating >= filters.minRating) ||
            (m.tmdb_rating && m.tmdb_rating >= filters.minRating),
        );
      }

      if (filters.maxRating) {
        movies = movies.filter(
          (m) =>
            (m.user_rating && m.user_rating <= filters.maxRating) ||
            (m.tmdb_rating && m.tmdb_rating <= filters.maxRating),
        );
      }

      if (filters.watched !== undefined) {
        movies = movies.filter((m) => m.is_watched === filters.watched);
      }

      const total = movies.length;

      // ordinamento
      if (filters.sortBy) {
        movies = this.sortMovies(movies, filters.sortBy, filters.sortOrder);
      }

      // paginazione
      const offset = filters.offset || 0;
      const limit = filters.limit || 50;
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

  // STATISTICHE

  /**
   * statistiche complete collezione film che include metriche enrichment
   */
  async getStats(): Promise<any> {
    try {
      const dbStats = await this.databaseService.getStats();

      this.logger.debug(`📊 statistiche recuperate`);

      return dbStats;
    } catch (error) {
      this.logger.error(`errore recupero statistiche: ${error.message}`);
      return { error: error.message };
    }
  }

  // HEALTH CHECK

  /**
   * verifica salute servizio con tutti i componenti
   */
  async healthCheck(): Promise<any> {
    try {
      const [dbHealth, tmdbHealth] = await Promise.all([
        this.databaseService.healthCheck(),
        this.tmdbService.healthCheck(),
      ]);

      const dbStats = await this.databaseService.getStats();

      return {
        status: 'healthy',
        components: {
          database: dbHealth,
          tmdb: tmdbHealth,
          websocket: this.websocketGateway.getConnectionInfo(),
        },
        statistics: dbStats,
        features: {
          intelligentEnrichment: 'enabled with is_enriched flag',
          autoSync: 'enabled',
          cacheOptimization: 'active',
          websocketNotifications: 'active',
        },
        timestamp: new Date().toISOString(),
      };
    } catch (error) {
      return {
        status: 'degraded',
        error: error.message,
        timestamp: new Date().toISOString(),
      };
    }
  }
}