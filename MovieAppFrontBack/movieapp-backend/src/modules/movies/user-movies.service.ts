// user-movies.service.ts
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
  //alias per compatibilità
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

  /**
   * associa lista di film a un utente con status specifico
   */
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

        if (!existing) {
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

  /**
   * associa film multipli ad un utente in batch
   */
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

      //conta film per tipo
      watchedInFile = movies.filter(m => m.status === MovieStatus.WATCHED).length;
      watchlistInFile = movies.filter(m => m.status === MovieStatus.WATCHLIST).length;

      //processa in chunk
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

  /**
   * recupera film con is_watched corretto
   */
  async getUserMovies(
    userId: string,
    status?: MovieStatus,
  ): Promise<Movie[]> {
    try {
      const query = this.userMovieRepository
        .createQueryBuilder('um')
        .leftJoinAndSelect('um.movie', 'movie')
        .where('um.userId = :userId', { userId });

      if (status) {
        query.andWhere('um.status = :status', { status });
      }

      const userMovies = await query.getMany();

      //conversione con is_watched corretto
      const movies: Movie[] = userMovies.map(um => ({
        id: um.movie.id,
        title: um.movie.title,
        year: um.movie.year,
        source: um.movie.source,
        tmdb_id: um.movie.tmdb_id,
        director: um.movie.director,
        genres: um.movie.genres,
        actors: um.movie.actors,
        overview: um.movie.overview,
        tagline: um.movie.tagline,
        runtime: um.movie.runtime,
        poster_url: um.movie.poster_url,
        backdrop_url: um.movie.backdrop_url,
        tmdb_rating: um.movie.tmdb_rating,
        vote_count: um.movie.vote_count,
        budget: um.movie.budget,
        revenue: um.movie.revenue,
        status: um.movie.status,
        original_language: um.movie.original_language,
        original_title: um.movie.original_title,
        popularity: um.movie.popularity,
        adult: um.movie.adult,
        homepage: um.movie.homepage,
        imdb_id: um.movie.imdb_id,
        production_companies: um.movie.production_companies,
        production_countries: um.movie.production_countries,
        spoken_languages: um.movie.spoken_languages,
        keywords: um.movie.keywords,
        certification: um.movie.certification,
        trailer_url: um.movie.trailer_url,
        
        //campi utente con is_watched corretto
        user_rating: um.userRating,
        watched_date: um.watchedDate?.toISOString(),
        is_watched: um.status === MovieStatus.WATCHED,
      }));

      this.logger.debug(`recuperati ${movies.length} film per utente ${userId}`);

      return movies;
    } catch (error) {
      this.logger.error(`errore getUserMovies: ${error.message}`);
      return [];
    }
  }

  /**
   * statistiche con query leggere
   */
  async getUserMovieStats(userId: string): Promise<UserMovieStats> {
    try {
      const [watched, watchlist, allUserMovies] = await Promise.all([
        this.userMovieRepository.count({
          where: { userId, status: MovieStatus.WATCHED },
        }),
        this.userMovieRepository.count({
          where: { userId, status: MovieStatus.WATCHLIST },
        }),
        this.userMovieRepository.find({
          where: { userId },
        }),
      ]);

      //calcola rating medio
      const ratingsWithValues = allUserMovies
        .map(um => um.userRating)
        .filter((r): r is number => r !== null && r !== undefined);

      const averageRating = ratingsWithValues.length > 0
        ? ratingsWithValues.reduce((sum, r) => sum + r, 0) / ratingsWithValues.length
        : 0;

      const totalMovies = watched + watchlist;

      this.logger.debug(
        `stats utente ${userId}: ${totalMovies} film (${watched} visti, ${watchlist} da vedere)`,
      );

      return {
        userId,
        totalMovies,
        watchedCount: watched,
        watchlistCount: watchlist,
        averageRating: parseFloat(averageRating.toFixed(2)),
        //alias per compatibilità
        watched,
        watchlist,
        total: totalMovies,
      };
    } catch (error) {
      this.logger.error(`errore getUserMovieStats: ${error.message}`);
      return {
        userId,
        totalMovies: 0,
        watchedCount: 0,
        watchlistCount: 0,
        averageRating: 0,
        watched: 0,
        watchlist: 0,
        total: 0,
      };
    }
  }

  /**
   * conteggi per import
   */
  async getImportCounters(userId: string): Promise<ImportCounters> {
    try {
      const stats = await this.getUserMovieStats(userId);

      return {
        watchedFromFile: 0,
        watchlistFromFile: 0,
        totalWatched: stats.watchedCount,
        totalWatchlist: stats.watchlistCount,
      };
    } catch (error) {
      this.logger.error(`errore getImportCounters: ${error.message}`);
      return {
        watchedFromFile: 0,
        watchlistFromFile: 0,
        totalWatched: 0,
        totalWatchlist: 0,
      };
    }
  }
}