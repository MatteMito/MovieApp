// file: src/modules/movies/movies.service.ts
// fix: associazione corretta user-movie con contatori accurati

import { Injectable, Logger } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { MovieEntity } from '../../database/entities/movie.entity';
import { UserMovieEntity, MovieStatus } from '../../database/entities/user-movie.entity';
import { Movie } from '../../common/interfaces/movie.interface';
import { TmdbService } from '../tmdb/tmdb.service';
import { DatabaseService } from '../../database/database.service';
import { WebsocketGateway } from '../websocket/websocket.gateway';
import { v4 as uuidv4 } from 'uuid';

export interface FailedMovie {
  movie: Movie;
  error: string;
}

export interface EnrichmentResult {
  sessionId: string;
  successfulMovies: Movie[];
  failedMovies: FailedMovie[];
  totalProcessed: number;
  successRate: number;
  cacheHits: number;
}

export interface BatchUploadResult {
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
  importCounters?: {
    watchedFromFile: number;
    watchlistFromFile: number;
    totalWatched: number;
    totalWatchlist: number;
  };
}

@Injectable()
export class MoviesService {
  private readonly logger = new Logger(MoviesService.name);

  constructor(
    @InjectRepository(MovieEntity)
    private readonly movieRepository: Repository<MovieEntity>,

    @InjectRepository(UserMovieEntity)
    private readonly userMovieRepository: Repository<UserMovieEntity>,

    private readonly tmdbService: TmdbService,
    private readonly databaseService: DatabaseService,
    private readonly websocketGateway: WebsocketGateway,
  ) {}

  /**
   * enrich movies con notifica websocket
   */
  async enrichMovies(movies: Movie[]): Promise<EnrichmentResult> {
    const sessionId = uuidv4();
    const successfulMovies: Movie[] = [];
    const failedMovies: FailedMovie[] = [];
    const totalMovies = movies.length;

    //notifica inizio
    await this.websocketGateway.notifyEnrichmentStarted(sessionId, totalMovies);

    for (let i = 0; i < movies.length; i++) {
      const movie = movies[i];
      const currentProgress = i + 1;

      try {
        const enrichedMovie = await this.tmdbService.enrichMovie(movie);
        
        if (enrichedMovie && enrichedMovie.tmdb_id) {
          successfulMovies.push(enrichedMovie);
          await this.databaseService.saveMovie(enrichedMovie);
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

        await this.sleep(300);

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

    const successRate = (successfulMovies.length / movies.length) * 100;

    return {
      sessionId,
      successfulMovies,
      failedMovies,
      totalProcessed: movies.length,
      successRate,
      cacheHits: 0,
    };
  }

  /**
   * batch upload base
   */
  async batchUpload(
    watchlist: Movie[],
    watched: Movie[],
    userId?: string,
  ): Promise<BatchUploadResult> {
    this.logger.log(`batch upload: ${watchlist.length} watchlist, ${watched.length} watched`);

    const [watchlistResult, watchedResult] = await Promise.all([
      this.enrichMovies(watchlist),
      this.enrichMovies(watched),
    ]);

    const totalEnriched =
      watchlistResult.successfulMovies.length + watchedResult.successfulMovies.length;
    const totalProcessed = watchlistResult.totalProcessed + watchedResult.totalProcessed;

    return {
      sessionId: uuidv4(),
      watchlistResult,
      watchedResult,
      summary: {
        totalMovies: totalProcessed,
        watchlistCount: watchlist.length,
        watchedCount: watched.length,
        totalEnriched,
        overallSuccessRate: (totalEnriched / totalProcessed) * 100,
        cacheHitsTotal: 0,
      },
    };
  }

  /**
   * fix: batch upload con associazione user-movie corretta
   */
  async batchUploadWithUserAssociation(
    userId: string,
    watchlist: Movie[],
    watched: Movie[],
  ): Promise<BatchUploadResult> {
    this.logger.log(`batch upload per user ${userId}: ${watchlist.length} watchlist, ${watched.length} watched`);

    //step 1: enrichment
    const result = await this.batchUpload(watchlist, watched, userId);
    
    this.logger.log(`creazione associazioni user_movies...`);
    
    const watchlistMovies = result.watchlistResult.successfulMovies;
    const watchedMovies = result.watchedResult.successfulMovies;
    
    let watchedCreated = 0;
    let watchlistCreated = 0;
    
    //step 2a: crea associazioni per watchlist
    for (const movie of watchlistMovies) {
      try {
        const existing = await this.userMovieRepository.findOne({
          where: {
            userId: userId,
            movieId: movie.id,
          },
        });
        
        if (!existing) {
          const userMovie = this.userMovieRepository.create({
            userId: userId,
            movieId: movie.id,
            status: MovieStatus.WATCHLIST,
            userRating: null,
            watchedDate: null,
          });
          await this.userMovieRepository.save(userMovie);
          watchlistCreated++;
          this.logger.debug(`associato watchlist: ${movie.title}`);
        }
      } catch (error) {
        this.logger.warn(`errore associazione ${movie.title}: ${error.message}`);
      }
    }
    
    //step 2b: crea associazioni per watched
    for (const movie of watchedMovies) {
      try {
        const existing = await this.userMovieRepository.findOne({
          where: {
            userId: userId,
            movieId: movie.id,
          },
        });
        
        if (!existing) {
          const userMovie = this.userMovieRepository.create({
            userId: userId,
            movieId: movie.id,
            status: MovieStatus.WATCHED,
            userRating: movie.user_rating || null,
            watchedDate: movie.watched_date ? new Date(movie.watched_date) : null,
          });
          await this.userMovieRepository.save(userMovie);
          watchedCreated++;
          this.logger.debug(`associato watched: ${movie.title}`);
        }
      } catch (error) {
        this.logger.warn(`errore associazione ${movie.title}: ${error.message}`);
      }
    }
    
    this.logger.log(`associazioni create: ${watchedCreated} watched, ${watchlistCreated} watchlist`);
    
    //step 3: conta i totali nel database
    const totalWatched = await this.userMovieRepository.count({
      where: { userId, status: MovieStatus.WATCHED },
    });
    
    const totalWatchlist = await this.userMovieRepository.count({
      where: { userId, status: MovieStatus.WATCHLIST },
    });
    
    this.logger.log(`totali database: ${totalWatched} watched, ${totalWatchlist} watchlist`);

    //aggiungi contatori al risultato
    return {
      ...result,
      importCounters: {
        watchedFromFile: watched.length,
        watchlistFromFile: watchlist.length,
        totalWatched,
        totalWatchlist,
      },
    };
  }

  /**
   * utility: sleep
   */
  private sleep(ms: number): Promise<void> {
    return new Promise(resolve => setTimeout(resolve, ms));
  }
}