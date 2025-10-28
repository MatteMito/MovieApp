import { Injectable, Logger } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository, Not, IsNull } from 'typeorm';
import { MovieEntity } from '../../database/entities/movie.entity';
import { UserEntity } from '../../database/entities/user.entity';
import { UserMovieEntity, MovieStatus } from '../../database/entities/user-movie.entity';
import { DatabaseService } from '../../database/database.service';
import { TmdbService } from '../tmdb/tmdb.service';
import { WebsocketGateway } from '../websocket/websocket.gateway';
import { Movie, BatchUploadResult, EnrichmentResult, FailedMovie } from '../../common/interfaces/movie.interface';
import { v4 as uuidv4 } from 'uuid';

@Injectable()
export class MoviesService {
  private readonly logger = new Logger(MoviesService.name);

  constructor(
    @InjectRepository(MovieEntity)
    private movieRepository: Repository<MovieEntity>,
    @InjectRepository(UserEntity)
    private userRepository: Repository<UserEntity>,
    @InjectRepository(UserMovieEntity)
    private userMovieRepository: Repository<UserMovieEntity>,
    private databaseService: DatabaseService,
    private tmdbService: TmdbService,
    private websocketGateway: WebsocketGateway,
  ) {}

  /**
   * Health check
   */
  async healthCheck(): Promise<any> {
    try {
      const movieCount = await this.movieRepository.count();
      return {
        status: 'healthy',
        database: 'connected',
        movieCount,
        timestamp: new Date().toISOString(),
      };
    } catch (error) {
      return {
        status: 'unhealthy',
        error: error.message,
        timestamp: new Date().toISOString(),
      };
    }
  }

  /**
   * Get system stats
   */
  async getStats(): Promise<any> {
    const totalMovies = await this.movieRepository.count();
    const enrichedMovies = await this.movieRepository.count({
      where: { tmdb_id: Not(IsNull()) },
    });
    
    return {
      database: {
        totalMovies,
        enrichedMovies,
        enrichmentRate: totalMovies > 0 ? (enrichedMovies / totalMovies) * 100 : 0,
      },
      timestamp: new Date().toISOString(),
    };
  }

  /**
   * Get user stats (con UserMovieEntity)
   */
  async getUserStats(userId: string): Promise<any> {
    const totalMovies = await this.userMovieRepository.count({ where: { userId } });
    const watchedCount = await this.userMovieRepository.count({
      where: { userId, status: MovieStatus.WATCHED },
    });
    const watchlistCount = await this.userMovieRepository.count({
      where: { userId, status: MovieStatus.WATCHLIST },
    });

    return {
      totalMovies,
      watchedCount,
      watchlistCount,
    };
  }

  /**
   * Get movie by ID
   */
  async getMovieById(id: string): Promise<MovieEntity | null> {
    return this.movieRepository.findOne({ where: { id } });
  }

  /**
   * Get all movies for a user (con UserMovieEntity)
   */
  async getAllMovies(userId: string): Promise<MovieEntity[]> {
    const userMovies = await this.userMovieRepository.find({
      where: { userId },
      relations: ['movie'],
      order: { createdAt: 'DESC' },
    });

    return userMovies.map(um => um.movie);
  }

  /**
   * Delete all movies for a user (con UserMovieEntity)
   */
  async deleteAllMovies(userId: string): Promise<{ message: string; deletedCount: number }> {
    const userMovies = await this.userMovieRepository.find({
      where: { userId },
    });
    
    const deletedCount = userMovies.length;

    await this.userMovieRepository.remove(userMovies);

    this.logger.log(`🗑️ Eliminati ${deletedCount} film per user ${userId}`);

    return {
      message: 'All movies deleted successfully',
      deletedCount,
    };
  }

  /**
   * Get user movies with filters
   */
  async getUserMovies(userId: string, filters: any): Promise<any> {
    const queryBuilder = this.userMovieRepository
      .createQueryBuilder('um')
      .leftJoinAndSelect('um.movie', 'movie')
      .where('um.userId = :userId', { userId });

    if (filters.status) {
      queryBuilder.andWhere('um.status = :status', { status: filters.status });
    }

    if (filters.query) {
      queryBuilder.andWhere('movie.title ILIKE :query', { query: `%${filters.query}%` });
    }

    if (filters.genre) {
      queryBuilder.andWhere(':genre = ANY(movie.genres)', { genre: filters.genre });
    }

    if (filters.year) {
      queryBuilder.andWhere('movie.year = :year', { year: filters.year });
    }

    if (filters.director) {
      queryBuilder.andWhere('movie.director ILIKE :director', { director: `%${filters.director}%` });
    }

    if (filters.minRating !== undefined) {
      queryBuilder.andWhere('um.userRating >= :minRating', { minRating: filters.minRating });
    }
    if (filters.maxRating !== undefined) {
      queryBuilder.andWhere('um.userRating <= :maxRating', { maxRating: filters.maxRating });
    }

    const sortBy = filters.sortBy || 'createdAt';
    const sortOrder = filters.sortOrder || 'DESC';
    
    if (sortBy === 'title') {
      queryBuilder.orderBy('movie.title', sortOrder);
    } else if (sortBy === 'year') {
      queryBuilder.orderBy('movie.year', sortOrder);
    } else if (sortBy === 'userRating') {
      queryBuilder.orderBy('um.userRating', sortOrder);
    } else {
      queryBuilder.orderBy('um.createdAt', sortOrder);
    }

    if (filters.limit) {
      queryBuilder.limit(filters.limit);
    }
    if (filters.offset) {
      queryBuilder.offset(filters.offset);
    }

    const [userMovies, total] = await queryBuilder.getManyAndCount();
    const movies = userMovies.map(um => um.movie);

    return {
      movies,
      total,
    };
  }

  /**
   * Search movies
   */
  async searchMovies(filters: any): Promise<any> {
    const queryBuilder = this.movieRepository.createQueryBuilder('movie');

    if (filters.query) {
      queryBuilder.where('movie.title ILIKE :query', { query: `%${filters.query}%` });
    }

    if (filters.genre) {
      queryBuilder.andWhere(':genre = ANY(movie.genres)', { genre: filters.genre });
    }

    if (filters.year) {
      queryBuilder.andWhere('movie.year = :year', { year: filters.year });
    }

    if (filters.director) {
      queryBuilder.andWhere('movie.director ILIKE :director', { director: `%${filters.director}%` });
    }

    if (filters.minRating !== undefined) {
      queryBuilder.andWhere('movie.tmdb_rating >= :minRating', { minRating: filters.minRating });
    }

    if (filters.maxRating !== undefined) {
      queryBuilder.andWhere('movie.tmdb_rating <= :maxRating', { maxRating: filters.maxRating });
    }

    const sortBy = filters.sortBy || 'title';
    const sortOrder = filters.sortOrder || 'ASC';
    queryBuilder.orderBy(`movie.${sortBy}`, sortOrder);

    if (filters.limit) {
      queryBuilder.limit(filters.limit);
    }
    if (filters.offset) {
      queryBuilder.offset(filters.offset);
    }

    const [movies, total] = await queryBuilder.getManyAndCount();

    return {
      movies,
      total,
    };
  }

  /**
   * Enrich movies
   */
  async enrichMovies(movies: Movie[]): Promise<EnrichmentResult> {
    const sessionId = uuidv4();
    const successfulMovies: Movie[] = [];
    const failedMovies: FailedMovie[] = [];
    const totalMovies = movies.length;

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
            error: 'No TMDB data found',
          });
        }

        await this.websocketGateway.notifyEnrichmentProgress(
          sessionId,
          currentProgress,
          totalMovies,
          movie.title,
        );

        await this.sleep(300);

      } catch (error) {
        this.logger.warn(`⚠️ Errore enrichment "${movie.title}": ${error.message}`);
        failedMovies.push({
          movie,
          error: error.message,
        });
      }
    }

    const successRate = (successfulMovies.length / movies.length) * 100;

    return {
      sessionId: uuidv4(),
      successfulMovies,
      failedMovies,
      totalProcessed: movies.length,
      successRate,
      cacheHits: 0,
    };
  }

  /**
   * Batch upload
   */
  async batchUpload(
    watchlist: Movie[],
    watched: Movie[],
    userId?: string,
  ): Promise<BatchUploadResult> {
    this.logger.log(`📦 Batch upload: ${watchlist.length} watchlist, ${watched.length} watched`);

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
   * Batch upload with user association
   */
  async batchUploadWithUserAssociation(
    userId: string,
    watchlist: Movie[],
    watched: Movie[],
  ): Promise<any> {
    this.logger.log(`📦 Batch upload per user ${userId}: ${watchlist.length} watchlist, ${watched.length} watched`);

    const result = await this.batchUpload(watchlist, watched, userId);
    
    const totalWatched = await this.userMovieRepository.count({
      where: { userId, status: MovieStatus.WATCHED },
    });
    
    const totalWatchlist = await this.userMovieRepository.count({
      where: { userId, status: MovieStatus.WATCHLIST },
    });

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
   * Utility: sleep
   */
  private sleep(ms: number): Promise<void> {
    return new Promise(resolve => setTimeout(resolve, ms));
  }
}