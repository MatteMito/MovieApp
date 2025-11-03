//service con metodo initializeapp per primo avvio

import { Injectable, Logger } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository, Not, IsNull } from 'typeorm';
import { MovieEntity } from '../../database/entities/movie.entity';
import { UserMovieEntity } from '../../database/entities/user-movie.entity';
import { TmdbService } from '../tmdb/tmdb.service';
import { DatabaseService } from '../../database/database.service';
import { WebsocketGateway } from '../websocket/websocket.gateway';
import { Movie } from '../../common/interfaces/movie.interface';
import { v4 as uuidv4 } from 'uuid';
import { UserMoviesService } from './user-movies.service';
import { MovieStatus } from '../../database/entities/user-movie.entity';

interface FailedMovie {
  movie: Movie;
  error: string;
}

interface EnrichmentResult {
  sessionId: string;
  successfulMovies: Movie[];
  failedMovies: FailedMovie[];
  totalProcessed: number;
  successRate: number;
  cacheHits: number;
}

interface BatchResult {
  sessionId: string;
  watchlistResult: EnrichmentResult;
  watchedResult: EnrichmentResult;
  summary: {
    totalMovies: number;
    watchlistCount: number;
    watchedCount: number;
    totalEnriched: number;
    overallSuccessRate: number;
    cacheHitsTotal: number;
  };
  counters: {
    fromFile: {
      watched: number;
      watchlist: number;
      total: number;
    };
    afterRefresh: {
      totalWatched: number;
      totalWatchlist: number;
      total: number;
    };
  };
}

//interfaccia per batch upload result
interface BatchUploadResult {
  sessionId: string;
  watchlistResult: EnrichmentResult;
  watchedResult: EnrichmentResult;
  summary: {
    totalMovies: number;
    watchlistCount: number;
    watchedCount: number;
    totalEnriched: number;
    overallSuccessRate: number;
    cacheHitsTotal: number;
  };
  importCounters: {
    watchedFromFile: number;
    watchlistFromFile: number;
    totalWatched: number;
    totalWatchlist: number;
  };
}

@Injectable()
export class MoviesService {
  private readonly logger = new Logger(MoviesService.name);
  private isInitializing = false;

  constructor(
    @InjectRepository(MovieEntity)
    private readonly movieRepository: Repository<MovieEntity>,

    @InjectRepository(UserMovieEntity)
    private readonly userMovieRepository: Repository<UserMovieEntity>,

    private readonly tmdbService: TmdbService,
    private readonly databaseService: DatabaseService,
    private readonly websocketGateway: WebsocketGateway,
    private readonly userMoviesService: UserMoviesService,
  ) {}

  //============================================
  //initialize app - primo avvio
  //============================================
  async initializeApp(): Promise<{
    needsSync: boolean;
    moviesInDb: number;
    message: string;
  }> {
    try {
      this.logger.log('controllo inizializzazione database...');

      //conta film nel database
      const moviesCount = await this.movieRepository.count();

      this.logger.log(`film presenti nel database: ${moviesCount}`);

      //se ci sono gia film, non serve sincronizzare
      if (moviesCount >= 1000) {
        this.logger.log('database gia inizializzato');
        return {
          needsSync: false,
          moviesInDb: moviesCount,
          message: 'database gia inizializzato',
        };
      }

      //se gia in inizializzazione, ritorna
      if (this.isInitializing) {
        this.logger.log('inizializzazione gia in corso');
        return {
          needsSync: true,
          moviesInDb: moviesCount,
          message: 'inizializzazione in corso',
        };
      }

      //avvia sync in background
      this.isInitializing = true;
      this.logger.log('avvio sync iniziale film popolari...');

      //sync asincrono (non blocca la risposta)
      this.syncInitialMovies()
        .then(() => {
          this.logger.log('sync iniziale completato');
          this.isInitializing = false;
        })
        .catch((error) => {
          this.logger.error(`errore sync iniziale: ${error.message}`);
          this.isInitializing = false;
        });

      return {
        needsSync: true,
        moviesInDb: moviesCount,
        message: 'sync avviato in background',
      };
    } catch (error) {
      this.logger.error(`errore initializeapp: ${error.message}`);
      this.isInitializing = false;
      throw error;
    }
  }

  //sync iniziale film popolari (chiamato in background)
  private async syncInitialMovies(): Promise<void> {
    try {
      this.logger.log('inizio sync film popolari...');

      //carica top 10000 film piu popolari
      const result = await this.tmdbService.syncPopularMovies(10000);

      this.logger.log(`sync completato: ${result.synced} film sincronizzati, ${result.errors} errori`);
    } catch (error) {
      this.logger.error(`errore sync: ${error.message}`);
      throw error;
    }
  }

  //============================================
  //health check
  //============================================
  async healthCheck() {
    try {
      const movieCount = await this.movieRepository.count();
      const userMovieCount = await this.userMovieRepository.count();

      return {
        status: 'ok',
        database: 'connected',
        movies: movieCount,
        userMovies: userMovieCount,
        timestamp: new Date().toISOString(),
      };
    } catch (error) {
      this.logger.error(`health check failed: ${error.message}`);
      return {
        status: 'error',
        database: 'disconnected',
        error: error.message,
        timestamp: new Date().toISOString(),
      };
    }
  }

  //============================================
  //statistiche generali
  //============================================
  async getStats() {
    try {
      const totalMovies = await this.movieRepository.count();
      const enrichedMovies = await this.movieRepository.count({
        where: { tmdb_id: Not(IsNull()) },
      });
      const totalUserMovies = await this.userMovieRepository.count();

      return {
        database: {
          totalMovies,
          enrichedMovies,
          totalUserMovies,
          enrichmentRate: totalMovies > 0 
            ? ((enrichedMovies / totalMovies) * 100).toFixed(2) + '%'
            : '0%',
        },
        timestamp: new Date().toISOString(),
      };
    } catch (error) {
      this.logger.error(`errore stats: ${error.message}`);
      throw error;
    }
  }

  //============================================
  //enrich movies con notifica websocket + cache intelligente
  //============================================
  async enrichMovies(movies: Movie[]): Promise<EnrichmentResult> {
    const sessionId = uuidv4();
    const successfulMovies: Movie[] = [];
    const failedMovies: FailedMovie[] = [];
    let cacheHits = 0;
    const totalMovies = movies.length;

    this.logger.log(`enrichment batch: ${totalMovies} film`);

    //notifica inizio
    await this.websocketGateway.notifyEnrichmentStarted(sessionId, totalMovies);

    for (let i = 0; i < movies.length; i++) {
      const movie = movies[i];
      const currentProgress = i + 1;

      try {
        //step 1: controlla cache prima di chiamare tmdb
        const existingMovie = await this.databaseService.findMovieByTitleYear(
          movie.title,
          movie.year
        );

        let enrichedMovie: Movie;

        if (existingMovie && existingMovie.is_enriched && existingMovie.tmdb_id) {
          //cache hit: film gia arricchito, usa quello
          this.logger.debug(`cache hit: ${movie.title}`);
          enrichedMovie = {
            ...existingMovie,
            id: movie.id,  //preserva id originale
            source: movie.source  //preserva source originale
          };
          cacheHits++;
        } else {
          //cache miss: arricchisci con tmdb
          this.logger.debug(`enrichment tmdb: ${movie.title}`);
          enrichedMovie = await this.tmdbService.enrichMovie(movie);
          
          //salva solo se arricchito con successo
          if (enrichedMovie.tmdb_id) {
            await this.databaseService.saveMovie(enrichedMovie);
          }
        }

        if (enrichedMovie && enrichedMovie.tmdb_id) {
          successfulMovies.push(enrichedMovie);
        } else {
          failedMovies.push({
            movie,
            error: 'no tmdb data found',
          });
        }

        //notifica progress per ogni film
        await this.websocketGateway.notifyEnrichmentProgress(
          sessionId,
          currentProgress,
          totalMovies,
          movie.title,
        );

        //pausa per non sovraccaricare
        await this.sleep(200);

      } catch (error) {
        this.logger.warn(`errore enrichment "${movie.title}": ${error.message}`);
        failedMovies.push({
          movie,
          error: error.message,
        });
      }
    }

    //notifica completamento
    await this.websocketGateway.notifyEnrichmentCompleted(sessionId, totalMovies);

    const successRate = totalMovies > 0 
      ? (successfulMovies.length / totalMovies) * 100 
      : 0;

    this.logger.log(`enrichment completato:`);
    this.logger.log(`   successo: ${successfulMovies.length}/${totalMovies}`);
    this.logger.log(`   cache hits: ${cacheHits}`);
    this.logger.log(`   falliti: ${failedMovies.length}`);

    return {
      sessionId,
      successfulMovies,
      failedMovies,
      totalProcessed: totalMovies,
      successRate,
      cacheHits,
    };
  }

  //============================================
  //batch upload
  //============================================
  async batchUpload(
    watchlist: Movie[],
    watched: Movie[],
    userId: string,
  ): Promise<BatchResult> {
    const sessionId = uuidv4();

    this.logger.log(`batch upload per user ${userId}:`);
    this.logger.log(`  watchlist: ${watchlist.length} film`);
    this.logger.log(`  watched: ${watched.length} film`);

    //enrich entrambe le liste
    const watchlistResult = await this.enrichMovies(watchlist);
    const watchedResult = await this.enrichMovies(watched);

    //contatori dal file
    const fromFile = {
      watched: watched.length,
      watchlist: watchlist.length,
      total: watched.length + watchlist.length,
    };

    //associa film all'utente
    await this.userMoviesService.associateMoviesToUser(
      userId,
      watchlistResult.successfulMovies,
      'watchlist',
    );

    await this.userMoviesService.associateMoviesToUser(
      userId,
      watchedResult.successfulMovies,
      'watched',
    );

    //contatori dopo refresh
    const stats = await this.userMoviesService.getUserMovieStats(userId);
    const afterRefresh = {
      totalWatched: stats.watched,
      totalWatchlist: stats.watchlist,
      total: stats.total,
    };

    const summary = {
      totalMovies: watchlist.length + watched.length,
      watchlistCount: watchlist.length,
      watchedCount: watched.length,
      totalEnriched:
        watchlistResult.successfulMovies.length +
        watchedResult.successfulMovies.length,
      overallSuccessRate:
        ((watchlistResult.successfulMovies.length +
          watchedResult.successfulMovies.length) /
          (watchlist.length + watched.length)) *
        100,
      cacheHitsTotal:
        watchlistResult.cacheHits + watchedResult.cacheHits,
    };

    return {
      sessionId,
      watchlistResult,
      watchedResult,
      summary,
      counters: {
        fromFile,
        afterRefresh,
      },
    };
  }

  /**
   * batch upload con associazione user
   * fix definitivo: usa tmdb_id per trovare id corretto dopo enrichment
   */
  async batchUploadWithUserAssociation(
    userId: string,
    watchlist: Movie[],
    watched: Movie[],
  ): Promise<BatchUploadResult> {
    this.logger.log(`batch upload per user ${userId}: ${watchlist.length} watchlist, ${watched.length} watched`);

    //contatori dal file prima dell'enrichment
    const watchedFromFile = watched.length;
    const watchlistFromFile = watchlist.length;

    //step 1: enrichment
    this.logger.log(`enrichment batch: ${watchlist.length + watched.length} film`);
    const watchlistResult = await this.enrichMovies(watchlist);
    const watchedResult = await this.enrichMovies(watched);

    //step 2: associa film all'utente
    this.logger.log(`associazione film a user ${userId}...`);

    //associa watchlist
    for (const movie of watchlistResult.successfulMovies) {
      try {
        const dbMovie = await this.databaseService.findMovieByTmdbId(movie.tmdb_id);
        if (dbMovie) {
          await this.userMoviesService.associateMoviesToUser(
            userId,
            [{ ...movie, id: dbMovie.id }],
            'watchlist'
          );
        }
      } catch (error) {
        this.logger.warn(`errore associazione watchlist ${movie.title}: ${error.message}`);
      }
    }

    //associa watched
    for (const movie of watchedResult.successfulMovies) {
      try {
        const dbMovie = await this.databaseService.findMovieByTmdbId(movie.tmdb_id);
        if (dbMovie) {
          await this.userMoviesService.associateMoviesToUser(
            userId,
            [{ ...movie, id: dbMovie.id }],
            'watched'
          );
        }
      } catch (error) {
        this.logger.warn(`errore associazione watched ${movie.title}: ${error.message}`);
      }
    }

    //step 3: contatori dopo associazione
    const stats = await this.userMoviesService.getUserMovieStats(userId);

    const summary = {
      totalMovies: watchlist.length + watched.length,
      watchlistCount: watchlist.length,
      watchedCount: watched.length,
      totalEnriched:
        watchlistResult.successfulMovies.length +
        watchedResult.successfulMovies.length,
      overallSuccessRate:
        ((watchlistResult.successfulMovies.length +
          watchedResult.successfulMovies.length) /
          (watchlist.length + watched.length)) *
        100,
      cacheHitsTotal:
        watchlistResult.cacheHits + watchedResult.cacheHits,
    };

    return {
      sessionId: uuidv4(),
      watchlistResult,
      watchedResult,
      summary,
      importCounters: {
        watchedFromFile,
        watchlistFromFile,
        totalWatched: stats.watched,
        totalWatchlist: stats.watchlist,
      },
    };
  }

  //============================================
  //get user movies
  //============================================
  async getAllMovies(userId: string, status?: string): Promise<Movie[]> {
    try {
      this.logger.log(`recupero film per user ${userId} (status: ${status || 'all'})`);

      const queryBuilder = this.userMovieRepository
        .createQueryBuilder('um')
        .leftJoinAndSelect('um.movie', 'movie')
        .where('um.userId = :userId', { userId });

      if (status && ['watched', 'watchlist'].includes(status)) {
        queryBuilder.andWhere('um.status = :status', { status });
      }

      const userMovies = await queryBuilder.getMany();

      const movies = userMovies
        .filter(um => um.movie)
        .map(um => ({
          ...um.movie,
          user_rating: um.userRating,
          watched_date: um.watchedDate,
          user_review: um.userReview,
          is_favorite: um.isFavorite,
        }));

      this.logger.log(`trovati ${movies.length} film`);
      return movies;
    } catch (error) {
      this.logger.error(`errore getallmovies: ${error.message}`);
      throw error;
    }
  }

  //============================================
  //user stats
  //============================================
  async getUserStats(userId: string) {
    try {
      const stats = await this.userMoviesService.getUserMovieStats(userId);
      return stats;
    } catch (error) {
      this.logger.error(`errore getstats: ${error.message}`);
      throw error;
    }
  }

  //============================================
  //delete methods
  //============================================
  async deleteAllMovies(userId: string): Promise<{ deleted: number }> {
    try {
      this.logger.log(`eliminazione associazioni per user ${userId}`);

      const result = await this.userMovieRepository.delete({ userId });

      const deletedCount = result.affected || 0;

      this.logger.log(`eliminate ${deletedCount} associazioni`);

      return { deleted: deletedCount };
    } catch (error) {
      this.logger.error(`errore delete: ${error.message}`);
      throw error;
    }
  }

  //============================================
  //utility
  //============================================
  private sleep(ms: number): Promise<void> {
    return new Promise(resolve => setTimeout(resolve, ms));
  }
}