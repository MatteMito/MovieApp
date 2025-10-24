// File: src/modules/movies/user-movies.service.ts
// Service per gestione relazioni user-movie

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
   * Associa film multipli ad un utente in batch
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
      this.logger.log(`📦 Batch: ${movies.length} film per utente ${userId}`);

      let created = 0;
      let updated = 0;
      let watchedInFile = 0;
      let watchlistInFile = 0;

      // Conta film per tipo
      watchedInFile = movies.filter(m => m.status === MovieStatus.WATCHED).length;
      watchlistInFile = movies.filter(m => m.status === MovieStatus.WATCHLIST).length;

      // Processa in chunk
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

        this.logger.log(`✅ Chunk ${Math.floor(i / CHUNK_SIZE) + 1}: ${chunk.length} film`);
      }

      this.logger.log(`=== BATCH COMPLETATO ===`);
      this.logger.log(`Creati: ${created}, Aggiornati: ${updated}`);
      this.logger.log(`Watched: ${watchedInFile}, Watchlist: ${watchlistInFile}`);

      return { created, updated, watchedInFile, watchlistInFile };
    } catch (error) {
      this.logger.error(`Errore batch: ${error.message}`);
      throw error;
    }
  }

  /**
   * ✅ FIX PRINCIPALE: Recupera film con isWatched corretto
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

      // ✅ CONVERSIONE CON is_watched CORRETTO
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
        
        // ✅ FIX: Campi utente con is_watched CORRETTO
        user_rating: um.userRating,
        watched_date: um.watchedDate?.toISOString(),
        is_watched: um.status === MovieStatus.WATCHED,  // ✅ QUESTO È IL FIX!
      }));

      this.logger.debug(`📚 Recuperati ${movies.length} film per utente ${userId}`);

      return movies;
    } catch (error) {
      this.logger.error(`Errore getUserMovies: ${error.message}`);
      return [];
    }
  }

  /**
   * ✅ OTTIMIZZATO: Statistiche con query leggere
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

      // Calcola rating medio
      const ratingsWithValues = allUserMovies
        .map(um => um.userRating)
        .filter((r): r is number => r !== null && r !== undefined);

      const averageRating = ratingsWithValues.length > 0
        ? ratingsWithValues.reduce((sum, r) => sum + r, 0) / ratingsWithValues.length
        : 0;

      const totalMovies = watched + watchlist;

      this.logger.debug(
        `📊 Stats utente ${userId}: ${totalMovies} film (${watched} visti, ${watchlist} da vedere)`,
      );

      return {
        userId,
        totalMovies,
        watchedCount: watched,
        watchlistCount: watchlist,
        averageRating: parseFloat(averageRating.toFixed(2)),
      };
    } catch (error) {
      this.logger.error(`Errore getUserMovieStats: ${error.message}`);
      return {
        userId,
        totalMovies: 0,
        watchedCount: 0,
        watchlistCount: 0,
        averageRating: 0,
      };
    }
  }

  /**
   * Conteggi per import
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
      this.logger.error(`Errore getImportCounters: ${error.message}`);
      return {
        watchedFromFile: 0,
        watchlistFromFile: 0,
        totalWatched: 0,
        totalWatchlist: 0,
      };
    }
  }
}