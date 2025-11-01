// movies.service.ts
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

  // ============================================
  // health check
  // ============================================
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

  // ============================================
  // statistiche generali
  // ============================================
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
          enrichmentRate: totalMovies > 0 ? (enrichedMovies / totalMovies) * 100 : 0,
        },
        timestamp: new Date().toISOString(),
      };
    } catch (error) {
      this.logger.error(`errore stats: ${error.message}`);
      throw error;
    }
  }

  // ============================================
  // enrich movies con notifica websocket + cache intelligente
  // ============================================
  async enrichMovies(movies: Movie[]): Promise<EnrichmentResult> {
    const sessionId = uuidv4();
    const successfulMovies: Movie[] = [];
    const failedMovies: FailedMovie[] = [];
    let cacheHits = 0;
    const totalMovies = movies.length;

    this.logger.log(`🎬 enrichment batch: ${totalMovies} film`);

    //notifica inizio
    await this.websocketGateway.notifyEnrichmentStarted(sessionId, totalMovies);

    for (let i = 0; i < movies.length; i++) {
      const movie = movies[i];
      const currentProgress = i + 1;

      try {
        // STEP 1: Controlla cache PRIMA di chiamare TMDB
        const existingMovie = await this.databaseService.findMovieByTitleYear(
          movie.title,
          movie.year
        );

        let enrichedMovie: Movie;

        if (existingMovie && existingMovie.is_enriched && existingMovie.tmdb_id) {
          // CACHE HIT: Film già arricchito, usa quello
          this.logger.debug(`💰 cache hit: ${movie.title}`);
          enrichedMovie = {
            ...existingMovie,
            id: movie.id,  //preserva id originale
            source: movie.source  //preserva source originale
          };
          cacheHits++;
        } else {
          // CACHE MISS: Arricchisci con TMDB
          this.logger.debug(`🔍 enrichment tmdb: ${movie.title}`);
          enrichedMovie = await this.tmdbService.enrichMovie(movie);
          
          // Salva solo se arricchito con successo
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

    this.logger.log(`✅ enrichment completato:`);
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

  // ============================================
  // batch upload
  // ============================================
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

  // ============================================
  // batch upload con associazione user
  // ============================================
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
    const watchlistResult = await this.enrichMovies(watchlist);
    const watchedResult = await this.enrichMovies(watched);
    
    this.logger.log(`creazione associazioni user_movies...`);
    
    const watchlistMovies = watchlistResult.successfulMovies;
    const watchedMovies = watchedResult.successfulMovies;
    
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
            userRating: movie.user_rating || null,
            watchedDate: null,
          });
          await this.userMovieRepository.save(userMovie);
          watchlistCreated++;
        }
      } catch (error) {
        this.logger.warn(`errore associazione watchlist ${movie.title}: ${error.message}`);
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
        }
      } catch (error) {
        this.logger.warn(`errore associazione watched ${movie.title}: ${error.message}`);
      }
    }
    
    this.logger.log(`associazioni create: ${watchlistCreated} watchlist, ${watchedCreated} watched`);
    
    //step 3: recupera conteggi totali dopo refresh
    const stats = await this.userMoviesService.getUserMovieStats(userId);
    
    const totalEnriched = watchlistResult.successfulMovies.length + watchedResult.successfulMovies.length;
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
        cacheHitsTotal: watchlistResult.cacheHits + watchedResult.cacheHits,
      },
      importCounters: {
        watchedFromFile,
        watchlistFromFile,
        totalWatched: stats.watched,
        totalWatchlist: stats.watchlist,
      },
    };
  }

  // ============================================
  // get user movies con filtri
  // ============================================
  async getUserMovies(
    userId: string,
    filters: {
      status?: string;
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
      this.logger.log(`recupero film per user ${userId} con filtri:`, filters);

      const movies = await this.userMoviesService.getUserMovies(
        userId,
        filters.status as any,
      );

      //applica filtri aggiuntivi
      let filteredMovies = movies;

      if (filters.query) {
        const query = filters.query.toLowerCase();
        filteredMovies = filteredMovies.filter(
          (m) =>
            m.title.toLowerCase().includes(query) ||
            m.director?.toLowerCase().includes(query),
        );
      }

      if (filters.genre) {
        filteredMovies = filteredMovies.filter((m) =>
          m.genres?.some((g) =>
            g.toLowerCase().includes(filters.genre.toLowerCase()),
          ),
        );
      }

      if (filters.year) {
        filteredMovies = filteredMovies.filter((m) => m.year === filters.year);
      }

      if (filters.director) {
        filteredMovies = filteredMovies.filter((m) =>
          m.director?.toLowerCase().includes(filters.director.toLowerCase()),
        );
      }

      if (filters.minRating) {
        filteredMovies = filteredMovies.filter(
          (m) => (m.tmdb_rating || 0) >= filters.minRating,
        );
      }

      if (filters.maxRating) {
        filteredMovies = filteredMovies.filter(
          (m) => (m.tmdb_rating || 0) <= filters.maxRating,
        );
      }

      //ordinamento
      if (filters.sortBy) {
        filteredMovies.sort((a, b) => {
          const aVal = a[filters.sortBy] || '';
          const bVal = b[filters.sortBy] || '';
          
          if (filters.sortOrder === 'DESC') {
            return bVal > aVal ? 1 : -1;
          }
          return aVal > bVal ? 1 : -1;
        });
      }

      const total = filteredMovies.length;

      //paginazione
      if (filters.limit) {
        const offset = filters.offset || 0;
        filteredMovies = filteredMovies.slice(offset, offset + filters.limit);
      }

      return { movies: filteredMovies, total };
    } catch (error) {
      this.logger.error(`errore getUserMovies: ${error.message}`);
      throw error;
    }
  }

  // ============================================
  // statistiche utente
  // ============================================
  async getUserStats(userId: string) {
    try {
      return await this.userMoviesService.getUserMovieStats(userId);
    } catch (error) {
      this.logger.error(`errore getUserStats: ${error.message}`);
      throw error;
    }
  }

  // ============================================
  // get singolo film
  // ============================================
  async getMovieById(id: string): Promise<Movie | null> {
    try {
      const movie = await this.movieRepository.findOne({ where: { id } });
      return movie as Movie;
    } catch (error) {
      this.logger.error(`errore getMovieById: ${error.message}`);
      return null;
    }
  }

  // ============================================
  // ricerca film
  // ============================================
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
      const queryBuilder = this.movieRepository.createQueryBuilder('movie');

      if (filters.query) {
        queryBuilder.andWhere(
          '(LOWER(movie.title) LIKE :query OR LOWER(movie.director) LIKE :query)',
          { query: `%${filters.query.toLowerCase()}%` },
        );
      }

      if (filters.genre) {
        queryBuilder.andWhere(':genre = ANY(movie.genres)', {
          genre: filters.genre,
        });
      }

      if (filters.year) {
        queryBuilder.andWhere('movie.year = :year', { year: filters.year });
      }

      if (filters.director) {
        queryBuilder.andWhere('LOWER(movie.director) LIKE :director', {
          director: `%${filters.director.toLowerCase()}%`,
        });
      }

      if (filters.minRating !== undefined) {
        queryBuilder.andWhere('movie.tmdb_rating >= :minRating', {
          minRating: filters.minRating,
        });
      }

      if (filters.maxRating !== undefined) {
        queryBuilder.andWhere('movie.tmdb_rating <= :maxRating', {
          maxRating: filters.maxRating,
        });
      }

      const total = await queryBuilder.getCount();

      //ordinamento
      const sortBy = filters.sortBy || 'title';
      const sortOrder = filters.sortOrder || 'ASC';
      queryBuilder.orderBy(`movie.${sortBy}`, sortOrder);

      //paginazione
      if (filters.limit) {
        queryBuilder.limit(filters.limit);
      }
      if (filters.offset) {
        queryBuilder.offset(filters.offset);
      }

      const movies = await queryBuilder.getMany();

      return { movies: movies as Movie[], total };
    } catch (error) {
      this.logger.error(`errore searchMovies: ${error.message}`);
      throw error;
    }
  }

  // ============================================
  // get tutti i film (deprecato)
  // ============================================
  async getAllMovies(userId: string): Promise<Movie[]> {
    try {
      const result = await this.getUserMovies(userId, {});
      return result.movies;
    } catch (error) {
      this.logger.error(`errore getAllMovies: ${error.message}`);
      throw error;
    }
  }

  // ============================================
  // elimina tutti i film utente
  // ============================================
  async deleteAllMovies(userId: string): Promise<{ deleted: number }> {
    try {
      const result = await this.userMovieRepository.delete({ userId });
      this.logger.log(`eliminati ${result.affected} film per user ${userId}`);
      return { deleted: result.affected || 0 };
    } catch (error) {
      this.logger.error(`errore deleteAllMovies: ${error.message}`);
      throw error;
    }
  }

  // ============================================
  // utility
  // ============================================
  private sleep(ms: number): Promise<void> {
    return new Promise((resolve) => setTimeout(resolve, ms));
  }
}