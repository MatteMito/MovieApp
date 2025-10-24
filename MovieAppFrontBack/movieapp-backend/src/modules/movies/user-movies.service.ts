// File: src/modules/movies/user-movies.service.ts
// Servizio per gestire le relazioni utente-film

import { Injectable, Logger } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { UserMovieEntity, MovieStatus } from '../../database/entities/user-movie.entity';
import { MovieEntity } from '../../database/entities/movie.entity';
import { Movie } from '../../common/interfaces/movie.interface';

export interface UserMovieStats {
  totalMovies: number;
  watchedCount: number;
  watchlistCount: number;
  averageRating?: number;
  lastImportDate?: Date;
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
    private userMovieRepository: Repository<UserMovieEntity>,
    
    @InjectRepository(MovieEntity)
    private movieRepository: Repository<MovieEntity>,
  ) {}

  /**
   * Associa un singolo film ad un utente
   */
  async associateMovieToUser(
    userId: string,
    movieId: string,
    status: MovieStatus,
    source: string,
    userRating?: number,
    watchedDate?: Date,
    userReview?: string,
  ): Promise<UserMovieEntity> {
    try {
      let userMovie = await this.userMovieRepository.findOne({
        where: { userId, movieId },
      });

      if (userMovie) {
        // Aggiorna esistente
        userMovie.status = status;
        userMovie.source = source;
        userMovie.userRating = userRating;
        userMovie.watchedDate = watchedDate;
        userMovie.userReview = userReview;
        
        this.logger.debug(`🔄 Aggiornato: user ${userId} - movie ${movieId}`);
      } else {
        // Crea nuovo
        userMovie = this.userMovieRepository.create({
          userId,
          movieId,
          status,
          source,
          userRating,
          watchedDate,
          userReview,
        });
        
        this.logger.debug(`➕ Creato: user ${userId} - movie ${movieId} [${status}]`);
      }

      return await this.userMovieRepository.save(userMovie);
    } catch (error) {
      this.logger.error(`Errore associazione: ${error.message}`);
      throw error;
    }
  }

  /**
   * Associa multipli film in batch
   */
  async batchAssociateMoviesToUser(
    userId: string,
    movies: Array<{
      movieId: string;
      status: MovieStatus;
      source: string;
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
            existing.source = movie.source;
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
              source: movie.source,
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
   * Recupera tutti i film di un utente
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

      // Converti in Movie[] 
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
      }));

      this.logger.debug(`📚 Recuperati ${movies.length} film per utente ${userId}`);

      return movies;
    } catch (error) {
      this.logger.error(`Errore getUserMovies: ${error.message}`);
      return [];
    }
  }

  /**
   * Statistiche utente
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
        ? ratingsWithValues.reduce((a, b) => a + b, 0) / ratingsWithValues.length
        : undefined;

      // Trova ultima data import
      const lastImportDate = allUserMovies.length > 0
        ? allUserMovies.reduce((latest, current) => 
            current.createdAt > latest ? current.createdAt : latest,
            allUserMovies[0].createdAt
          )
        : undefined;

      return {
        totalMovies: watched + watchlist,
        watchedCount: watched,
        watchlistCount: watchlist,
        averageRating,
        lastImportDate,
      };
    } catch (error) {
      this.logger.error(`Errore getUserMovieStats: ${error.message}`);
      throw error;
    }
  }

  /**
   * Elimina associazione utente-film
   */
  async removeUserMovie(userId: string, movieId: string): Promise<void> {
    try {
      await this.userMovieRepository.delete({ userId, movieId });
      this.logger.debug(`🗑️ Rimosso: user ${userId} - movie ${movieId}`);
    } catch (error) {
      this.logger.error(`Errore removeUserMovie: ${error.message}`);
      throw error;
    }
  }

  /**
   * Aggiorna rating utente per un film
   */
  async updateUserRating(
    userId: string,
    movieId: string,
    rating: number,
  ): Promise<UserMovieEntity> {
    try {
      const userMovie = await this.userMovieRepository.findOne({
        where: { userId, movieId },
      });

      if (!userMovie) {
        throw new Error('User movie not found');
      }

      userMovie.userRating = rating;
      return await this.userMovieRepository.save(userMovie);
    } catch (error) {
      this.logger.error(`Errore updateUserRating: ${error.message}`);
      throw error;
    }
  }
}