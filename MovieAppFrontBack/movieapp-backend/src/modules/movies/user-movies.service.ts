//file: src/modules/movies/user-movies.service.ts
//service gestione associazioni user-film con gestione duplicati

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
            //aggiorna solo se status diverso
            if (existing.status !== movieStatus) {
              existing.status = movieStatus;
              existing.userRating = movie.user_rating || existing.userRating;
              existing.watchedDate = movie.watched_date 
                ? new Date(movie.watched_date) 
                : existing.watchedDate;
              await this.userMovieRepository.save(existing);
              updated++;
              this.logger.log(`aggiornato: ${movie.title} (${existing.status} -> ${movieStatus})`);
            } else {
              //gia presente con stesso status, skip
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
          }
        } catch (error) {
          this.logger.error(`errore processing film ${movie.title}: ${error.message}`);
          skipped++;
        }
      }

      this.logger.log(`=== associazione completata ===`);
      this.logger.log(`creati: ${created}`);
      this.logger.log(`aggiornati: ${updated}`);
      this.logger.log(`skippati (duplicati): ${skipped}`);
      this.logger.log(`totale: ${created + updated}/${movies.length} film associati`);

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

      return userMovies
        .filter(um => um.movie)
        .map(um => this.userMovieToMovie(um));
    } catch (error) {
      this.logger.error(`errore getUserMovies: ${error.message}`);
      throw error;
    }
  }

  async getUserMovieStats(userId: string): Promise<UserMovieStats> {
    try {
      const [total, watched, watchlist] = await Promise.all([
        this.userMovieRepository.count({ where: { userId } }),
        this.userMovieRepository.count({
          where: { userId, status: MovieStatus.WATCHED },
        }),
        this.userMovieRepository.count({
          where: { userId, status: MovieStatus.WATCHLIST },
        }),
      ]);

      const ratedMovies = await this.userMovieRepository.find({
        where: { userId, status: MovieStatus.WATCHED },
        select: ['userRating'],
      });

      const ratingsSum = ratedMovies
        .filter(um => um.userRating !== null && um.userRating !== undefined)
        .reduce((sum, um) => sum + um.userRating, 0);

      const ratedCount = ratedMovies.filter(
        um => um.userRating !== null && um.userRating !== undefined,
      ).length;

      const averageRating = ratedCount > 0 ? ratingsSum / ratedCount : 0;

      return {
        userId,
        totalMovies: total,
        watchedCount: watched,
        watchlistCount: watchlist,
        averageRating: Math.round(averageRating * 10) / 10,
        watched,
        watchlist,
        total,
      };
    } catch (error) {
      this.logger.error(`errore getUserMovieStats: ${error.message}`);
      throw error;
    }
  }

  private userMovieToMovie(userMovie: UserMovieEntity): Movie {
    const movie = userMovie.movie;
    return {
      id: movie.id,
      title: movie.title,
      year: movie.year,
      source: movie.source,
      tmdb_id: movie.tmdb_id,
      is_enriched: movie.is_enriched,
      genres: movie.genres,
      director: movie.director,
      actors: movie.actors,
      overview: movie.overview,
      tagline: movie.tagline,
      runtime: movie.runtime,
      poster_url: movie.poster_url,
      backdrop_url: movie.backdrop_url,
      tmdb_rating: movie.tmdb_rating,
      vote_count: movie.vote_count,
      popularity: movie.popularity,
      budget: movie.budget,
      revenue: movie.revenue,
      status: userMovie.status,
      production_companies: movie.production_companies,
      production_countries: movie.production_countries,
      original_language: movie.original_language,
      original_title: movie.original_title,
      spoken_languages: movie.spoken_languages,
      adult: movie.adult,
      homepage: movie.homepage,
      imdb_id: movie.imdb_id,
      keywords: movie.keywords,
      certification: movie.certification,
      trailer_url: movie.trailer_url,
      user_rating: userMovie.userRating,
      watched_date: userMovie.watchedDate,
      user_review: userMovie.userReview,
      is_favorite: userMovie.isFavorite,
      created_at: movie.created_at,
      updated_at: movie.updated_at,
    };
  }
}