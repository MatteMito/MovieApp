//file: src/modules/movies/user-movies.service.ts
//service gestione associazioni user-film con gestione duplicati e priorità watched

import { Injectable, Logger } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import {
  UserMovieEntity,
  MovieStatus,
} from '../../database/entities/user-movie.entity';
import { MovieEntity } from '../../database/entities/movie.entity';
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
    @InjectRepository(MovieEntity)
    private readonly movieRepository: Repository<MovieEntity>,
  ) {}

  async associateMoviesToUser(
    userId: string,
    movies: Movie[],
    status: 'watched' | 'watchlist',
  ): Promise<void> {
    try {
      this.logger.log(`associazione ${movies.length} film (${status}) a user ${userId}`);

      const movieStatus = status === 'watched' ? MovieStatus.WATCHED : MovieStatus.WATCHLIST;

      let created = 0;
      let updated = 0;
      let skipped = 0;

      //usa batch upsert per evitare duplicati
      for (const movie of movies) {
        try {
          //verifica che il film esista nella tabella movies
          const movieExists = await this.movieRepository.findOne({
            where: { id: movie.id },
          });

          if (!movieExists) {
            this.logger.warn(`film ${movie.title} (${movie.id}) non trovato in tabella movies, skip`);
            skipped++;
            continue;
          }

          //cerca se esiste gia l'associazione
          const existing = await this.userMovieRepository.findOne({
            where: { userId, movieId: movie.id },
          });

          if (existing) {
            //priorità: watched ha sempre la precedenza su watchlist
            //se il film è già watched, non cambiare lo stato anche se arriva come watchlist
            if (existing.status === MovieStatus.WATCHED && movieStatus === MovieStatus.WATCHLIST) {
              this.logger.debug(`film ${movie.title} già WATCHED, skip aggiornamento a WATCHLIST`);
              skipped++;
              continue;
            }

            //altrimenti aggiorna se status diverso
            if (existing.status !== movieStatus) {
              existing.status = movieStatus;
              existing.userRating = movie.user_rating || existing.userRating;
              existing.watchedDate = movie.watched_date 
                ? new Date(movie.watched_date) 
                : existing.watchedDate;
              await this.userMovieRepository.save(existing);
              updated++;
              this.logger.debug(`aggiornato ${movie.title} da ${existing.status} a ${movieStatus}`);
            } else {
              skipped++;
            }
          } else {
            //crea nuova associazione
            const userMovie = this.userMovieRepository.create({
              userId,
              movieId: movie.id,
              status: movieStatus,
              userRating: movie.user_rating,
              watchedDate: movie.watched_date ? new Date(movie.watched_date) : null,
            });

            await this.userMovieRepository.save(userMovie);
            created++;

            if (created <= 5) {
              this.logger.debug(`creato: ${movie.title} (${movieStatus})`);
            }
          }
        } catch (error) {
          this.logger.error(`errore associazione film ${movie.title}:`, error);
          skipped++;
        }
      }

      this.logger.log(`associazione completata:`);
      this.logger.log(`  creati: ${created}`);
      this.logger.log(`  aggiornati: ${updated}`);
      this.logger.log(`  skippati: ${skipped}`);

    } catch (error) {
      this.logger.error('errore associazione batch movies:', error);
      throw error;
    }
  }

  async getUserMovieStats(userId: string): Promise<UserMovieStats> {
    try {
      const allMovies = await this.userMovieRepository.find({
        where: { userId },
      });

      const watched = allMovies.filter(m => m.status === MovieStatus.WATCHED).length;
      const watchlist = allMovies.filter(m => m.status === MovieStatus.WATCHLIST).length;

      const ratingsSum = allMovies
        .filter(m => m.userRating !== null && m.userRating !== undefined)
        .reduce((sum, m) => sum + (m.userRating || 0), 0);
      
      const ratingsCount = allMovies.filter(m => m.userRating).length;
      const averageRating = ratingsCount > 0 ? ratingsSum / ratingsCount : 0;

      return {
        userId,
        totalMovies: allMovies.length,
        watchedCount: watched,
        watchlistCount: watchlist,
        averageRating: Math.round(averageRating * 10) / 10,
        watched,
        watchlist,
        total: allMovies.length,
      };
    } catch (error) {
      this.logger.error(`errore recupero stats user ${userId}:`, error);
      throw error;
    }
  }

  async getUserMovies(
    userId: string,
    status?: 'watched' | 'watchlist',
  ): Promise<Movie[]> {
    try {
      const queryBuilder = this.userMovieRepository
        .createQueryBuilder('userMovie')
        .leftJoinAndSelect('userMovie.movie', 'movie')
        .where('userMovie.userId = :userId', { userId });

      if (status) {
        const movieStatus = status === 'watched' ? MovieStatus.WATCHED : MovieStatus.WATCHLIST;
        queryBuilder.andWhere('userMovie.status = :status', { status: movieStatus });
      }

      const userMovies = await queryBuilder.getMany();

      return userMovies.map(um => ({
        ...um.movie,
        user_rating: um.userRating,
        watched_date: um.watchedDate?.toISOString(),
        status: um.status === MovieStatus.WATCHED ? 'watched' : 'watchlist',
      }));
    } catch (error) {
      this.logger.error(`errore recupero movies user ${userId}:`, error);
      throw error;
    }
  }

  async deleteUserMovie(userId: string, movieId: string): Promise<void> {
    try {
      await this.userMovieRepository.delete({ userId, movieId });
      this.logger.log(`eliminato film ${movieId} per user ${userId}`);
    } catch (error) {
      this.logger.error(`errore eliminazione film ${movieId}:`, error);
      throw error;
    }
  }

  async deleteAllUserMovies(userId: string): Promise<void> {
    try {
      await this.userMovieRepository.delete({ userId });
      this.logger.log(`eliminati tutti i film per user ${userId}`);
    } catch (error) {
      this.logger.error(`errore eliminazione film user ${userId}:`, error);
      throw error;
    }
  }
}