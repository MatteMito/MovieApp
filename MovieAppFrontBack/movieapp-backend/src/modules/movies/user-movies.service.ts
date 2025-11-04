//user-movies.service.ts
import { Injectable, Logger } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import {
  UserMovieEntity,
  MovieStatus,
} from '../../database/entities/user-movie.entity';
import { Movie } from '../../common/interfaces/movie.interface';

export interface UserMovieStats {
  userId: string;
  totalMovies: number;
  watchedCount: number;
  watchlistCount: number;
  averageRating: number;
  watched: number;
  watchlist: number;
  total: number;
}

export interface ImportCounters {
  watchedFromFile: number;
  watchlistFromFile: number;
  totalWatched: number;
  totalWatchlist: number;
}

@Injectable()
export class UserMoviesService {
  private readonly logger = new Logger(UserMoviesService.name);

  constructor(
    @InjectRepository(UserMovieEntity)
    private readonly userMovieRepository: Repository<UserMovieEntity>,
  ) {}

  async associateMoviesToUser(
    userId: string,
    movies: Movie[],
    status: 'watched' | 'watchlist',
  ): Promise<void> {
    try {
      this.logger.log(`associazione ${movies.length} film (${status}) a user ${userId}`);

      const movieStatus = status === 'watched' ? MovieStatus.WATCHED : MovieStatus.WATCHLIST;

      for (const movie of movies) {
        const existing = await this.userMovieRepository.findOne({
          where: { userId, movieId: movie.id },
        });

        if (existing) {
          if (existing.status !== movieStatus) {
            existing.status = movieStatus;
            existing.userRating = movie.user_rating || existing.userRating;
            existing.watchedDate = movie.watched_date ? new Date(movie.watched_date) : existing.watchedDate;
            await this.userMovieRepository.save(existing);
          }
        } else {
          const userMovie = this.userMovieRepository.create({
            userId,
            movieId: movie.id,
            status: movieStatus,
            userRating: movie.user_rating,
            watchedDate: movie.watched_date ? new Date(movie.watched_date) : null,
          });
          await this.userMovieRepository.save(userMovie);
        }
      }

      this.logger.log(`associati ${movies.length} film come ${status}`);
    } catch (error) {
      this.logger.error(`errore associateMoviesToUser: ${error.message}`);
      throw error;
    }
  }

  async batchAssociateMovies(
    userId: string,
    movies: Array<{
      movieId: string;
      status: MovieStatus;
      userRating?: number;
      watchedDate?: Date;
      userReview?: string;
    }>,
  ): Promise<{
    created: number;
    updated: number;
    watchedInFile: number;
    watchlistInFile: number;
  }> {
    try {
      this.logger.log(`batch: ${movies.length} film per utente ${userId}`);

      let created = 0;
      let updated = 0;
      let watchedInFile = 0;
      let watchlistInFile = 0;

      watchedInFile = movies.filter(m => m.status === MovieStatus.WATCHED).length;
      watchlistInFile = movies.filter(m => m.status === MovieStatus.WATCHLIST).length;

      const CHUNK_SIZE = 100;
      for (let i = 0; i < movies.length; i += CHUNK_SIZE) {
        const chunk = movies.slice(i, i + CHUNK_SIZE);

        for (const movie of chunk) {
          const existing = await this.userMovieRepository.findOne({
            where: { userId, movieId: movie.movieId },
          });

          if (existing) {
            existing.status = movie.status;
            existing.userRating = movie.userRating;
            existing.watchedDate = movie.watchedDate;
            existing.userReview = movie.userReview;
            await this.userMovieRepository.save(existing);
            updated++;
          } else {
            const userMovie = this.userMovieRepository.create({
              userId,
              movieId: movie.movieId,
              status: movie.status,
              userRating: movie.userRating,
              watchedDate: movie.watchedDate,
              userReview: movie.userReview,
            });
            await this.userMovieRepository.save(userMovie);
            created++;
          }
        }

        this.logger.log(`chunk ${Math.floor(i / CHUNK_SIZE) + 1}: ${chunk.length} film`);
      }

      this.logger.log(`=== batch completato ===`);
      this.logger.log(`creati: ${created}, aggiornati: ${updated}`);
      this.logger.log(`watched: ${watchedInFile}, watchlist: ${watchlistInFile}`);

      return { created, updated, watchedInFile, watchlistInFile };
    } catch (error) {
      this.logger.error(`errore batch: ${error.message}`);
      throw error;
    }
  }

  async getUserMovies(
    userId: string,
    status?: MovieStatus,
  ): Promise<Movie[]> {
    try {
      const queryBuilder = this.userMovieRepository
        .createQueryBuilder('um')
        .leftJoinAndSelect('um.movie', 'movie')
        .where('um.userId = :userId', { userId });

      if (status) {
        queryBuilder.andWhere('um.status = :status', { status });
      }

      const userMovies = await queryBuilder.getMany();

      const result = userMovies
        .filter(um => um.movie)
        .map(um => {
          const isWatched = um.status === 'watched';
          
          return {
            ...um.movie,
            user_rating: um.userRating,
            watched_date: um.watchedDate,
            user_review: um.userReview,
            is_favorite: um.isFavorite,
            is_watched: isWatched,
          };
        });

      return result;
    } catch (error) {
      this.logger.error(`errore getUserMovies: ${error.message}`);
      throw error;
    }
  }

  async getUserMovieStats(userId: string): Promise<UserMovieStats> {
    try {
      const allMovies = await this.userMovieRepository.find({
        where: { userId },
      });

      const watchedMovies = allMovies.filter(um => um.status === MovieStatus.WATCHED);
      const watchlistMovies = allMovies.filter(um => um.status === MovieStatus.WATCHLIST);

      const watchedWithRating = watchedMovies.filter(um => um.userRating != null);
      const averageRating = watchedWithRating.length > 0
        ? watchedWithRating.reduce((sum, um) => sum + um.userRating, 0) / watchedWithRating.length
        : 0;

      return {
        userId,
        totalMovies: allMovies.length,
        watchedCount: watchedMovies.length,
        watchlistCount: watchlistMovies.length,
        averageRating,
        watched: watchedMovies.length,
        watchlist: watchlistMovies.length,
        total: allMovies.length,
      };
    } catch (error) {
      this.logger.error(`errore getUserMovieStats: ${error.message}`);
      throw error;
    }
  }
}