//servizio per gestione liste film personalizzate

import { Injectable, Logger } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { MovieEntity } from '../../database/entities/movie.entity';

//interfacce liste
export interface MovieList {
  name: string;
  description?: string;
  movies: MovieEntity[];
  createdAt: Date;
  totalMovies: number;
  totalRuntime: number;
  averageRating?: number;
}

export interface ListFilters {
  genre?: string;
  director?: string;
  minYear?: number;
  maxYear?: number;
  minRating?: number;
  maxRating?: number;
  watched?: boolean;
  hasRating?: boolean;
  sortBy?: 'title' | 'year' | 'rating' | 'runtime';
  sortOrder?: 'ASC' | 'DESC';
}

@Injectable()
export class ListsService {
  private readonly logger = new Logger(ListsService.name);

  constructor(
    @InjectRepository(MovieEntity)
    private movieRepository: Repository<MovieEntity>,
  ) {}

  /**
   * crea lista personalizzata con filtri
   */
  async createCustomList(
    name: string,
    filters: ListFilters,
    description?: string,
  ): Promise<MovieList> {
    try {
      this.logger.log(`creazione lista personalizzata: ${name}`);

      const movies = await this.filterMovies(filters);

      //calcola statistiche lista
      const totalRuntime = movies
        .map((m) => m.runtime || 0)
        .reduce((a, b) => a + b, 0);

      const ratingsWithValues = movies
        .map((m) => m.user_rating || m.tmdb_rating)
        .filter((r): r is number => r !== null && r !== undefined);

      const averageRating =
        ratingsWithValues.length > 0
          ? ratingsWithValues.reduce((a, b) => a + b, 0) /
            ratingsWithValues.length
          : undefined;

      const list: MovieList = {
        name,
        description,
        movies,
        createdAt: new Date(),
        totalMovies: movies.length,
        totalRuntime,
        averageRating,
      };

      this.logger.log(`lista creata: ${movies.length} film`);
      return list;
    } catch (error) {
      this.logger.error(`errore creazione lista: ${error.message}`);
      throw error;
    }
  }

  /**
   * filtra film con criteri multipli
   */
  private async filterMovies(filters: ListFilters): Promise<MovieEntity[]> {
    try {
      let query = this.movieRepository.createQueryBuilder('movie');

      //filtro genere
      if (filters.genre) {
        query = query.andWhere(':genre = ANY(movie.genres)', {
          genre: filters.genre,
        });
      }

      //filtro regista
      if (filters.director) {
        query = query.andWhere('movie.director ILIKE :director', {
          director: `%${filters.director}%`,
        });
      }

      //filtro anno
      if (filters.minYear) {
        query = query.andWhere('movie.year >= :minYear', {
          minYear: filters.minYear,
        });
      }

      if (filters.maxYear) {
        query = query.andWhere('movie.year <= :maxYear', {
          maxYear: filters.maxYear,
        });
      }

      //filtro rating
      if (filters.minRating) {
        query = query.andWhere(
          '(movie.user_rating >= :minRating OR movie.tmdb_rating >= :minRating)',
          { minRating: filters.minRating },
        );
      }

      if (filters.maxRating) {
        query = query.andWhere(
          '(movie.user_rating <= :maxRating OR movie.tmdb_rating <= :maxRating)',
          { maxRating: filters.maxRating },
        );
      }

      //filtro watched
      if (filters.watched !== undefined) {
        query = query.andWhere('movie.is_watched = :watched', {
          watched: filters.watched,
        });
      }

      //filtro has rating
      if (filters.hasRating) {
        query = query.andWhere(
          '(movie.user_rating IS NOT NULL OR movie.tmdb_rating IS NOT NULL)',
        );
      }

      //ordinamento
      const sortBy = filters.sortBy || 'title';
      const sortOrder = filters.sortOrder || 'ASC';

      switch (sortBy) {
        case 'year':
          query = query.orderBy('movie.year', sortOrder, 'NULLS LAST');
          break;
        case 'rating':
          query = query.orderBy(
            'COALESCE(movie.user_rating, movie.tmdb_rating)',
            sortOrder,
            'NULLS LAST',
          );
          break;
        case 'runtime':
          query = query.orderBy('movie.runtime', sortOrder, 'NULLS LAST');
          break;
        default:
          query = query.orderBy('movie.title', sortOrder);
      }

      const movies = await query.getMany();

      this.logger.log(`filtro applicato: ${movies.length} film trovati`);
      return movies;
    } catch (error) {
      this.logger.error(`errore filtro: ${error.message}`);
      throw error;
    }
  }

  /**
   * liste predefinite
   */
  async getTopRatedMovies(limit: number = 50): Promise<MovieList> {
    try {
      this.logger.log(`recupero top ${limit} film`);

      const movies = await this.movieRepository
        .createQueryBuilder('movie')
        .where('movie.user_rating IS NOT NULL OR movie.tmdb_rating IS NOT NULL')
        .orderBy('COALESCE(movie.user_rating, movie.tmdb_rating)', 'DESC')
        .limit(limit)
        .getMany();

      return this.buildListFromMovies('Top Rated Movies', movies);
    } catch (error) {
      this.logger.error(`errore top rated: ${error.message}`);
      throw error;
    }
  }

  async getRecentMovies(limit: number = 50): Promise<MovieList> {
    try {
      this.logger.log(`recupero ${limit} film recenti`);

      const currentYear = new Date().getFullYear();

      const movies = await this.movieRepository
        .createQueryBuilder('movie')
        .where('movie.year >= :minYear', { minYear: currentYear - 5 })
        .orderBy('movie.year', 'DESC')
        .limit(limit)
        .getMany();

      return this.buildListFromMovies('Recent Movies', movies);
    } catch (error) {
      this.logger.error(`errore recent movies: ${error.message}`);
      throw error;
    }
  }

  async getClassicMovies(): Promise<MovieList> {
    try {
      this.logger.log('recupero film classici');

      const movies = await this.movieRepository
        .createQueryBuilder('movie')
        .where('movie.year >= :minYear AND movie.year <= :maxYear', {
          minYear: 1950,
          maxYear: 1999,
        })
        .orderBy('movie.year', 'ASC')
        .getMany();

      return this.buildListFromMovies('Classic Movies (1950-1999)', movies);
    } catch (error) {
      this.logger.error(`errore classic movies: ${error.message}`);
      throw error;
    }
  }

  async getLongMovies(minRuntime: number = 180): Promise<MovieList> {
    try {
      this.logger.log(`recupero film lunghi (>${minRuntime}min)`);

      const movies = await this.movieRepository
        .createQueryBuilder('movie')
        .where('movie.runtime >= :minRuntime', { minRuntime })
        .orderBy('movie.runtime', 'DESC')
        .getMany();

      return this.buildListFromMovies(`Long Movies (${minRuntime}+ min)`, movies);
    } catch (error) {
      this.logger.error(`errore long movies: ${error.message}`);
      throw error;
    }
  }

  async getMoviesByDecade(decade: number): Promise<MovieList> {
    try {
      this.logger.log(`recupero film anni ${decade}`);

      const movies = await this.movieRepository
        .createQueryBuilder('movie')
        .where('movie.year >= :startYear AND movie.year < :endYear', {
          startYear: decade,
          endYear: decade + 10,
        })
        .orderBy('movie.year', 'ASC')
        .getMany();

      return this.buildListFromMovies(`Movies from ${decade}s`, movies);
    } catch (error) {
      this.logger.error(`errore movies by decade: ${error.message}`);
      throw error;
    }
  }

  async getUnwatchedWatchlist(): Promise<MovieList> {
    try {
      this.logger.log('recupero watchlist non vista');

      const movies = await this.movieRepository
        .createQueryBuilder('movie')
        .where('movie.is_watched = :watched', { watched: false })
        .orderBy('movie.title', 'ASC')
        .getMany();

      return this.buildListFromMovies('Watchlist (Unwatched)', movies);
    } catch (error) {
      this.logger.error(`errore unwatched watchlist: ${error.message}`);
      throw error;
    }
  }

  async getMoviesByGenre(genre: string): Promise<MovieList> {
    try {
      this.logger.log(`recupero film genere: ${genre}`);

      const movies = await this.movieRepository
        .createQueryBuilder('movie')
        .where(':genre = ANY(movie.genres)', { genre })
        .orderBy('movie.title', 'ASC')
        .getMany();

      return this.buildListFromMovies(`${genre} Movies`, movies);
    } catch (error) {
      this.logger.error(`errore movies by genre: ${error.message}`);
      throw error;
    }
  }

  async getMoviesByDirector(director: string): Promise<MovieList> {
    try {
      this.logger.log(`recupero film regista: ${director}`);

      const movies = await this.movieRepository
        .createQueryBuilder('movie')
        .where('movie.director ILIKE :director', { director: `%${director}%` })
        .orderBy('movie.year', 'ASC')
        .getMany();

      return this.buildListFromMovies(`Movies by ${director}`, movies);
    } catch (error) {
      this.logger.error(`errore movies by director: ${error.message}`);
      throw error;
    }
  }

  /**
   * helper: costruisce movielist da array film
   */
  private buildListFromMovies(
    name: string,
    movies: MovieEntity[],
  ): MovieList {
    const totalRuntime = movies
      .map((m) => m.runtime || 0)
      .reduce((a, b) => a + b, 0);

    const ratingsWithValues = movies
      .map((m) => m.user_rating || m.tmdb_rating)
      .filter((r): r is number => r !== null && r !== undefined);

    const averageRating =
      ratingsWithValues.length > 0
        ? ratingsWithValues.reduce((a, b) => a + b, 0) /
          ratingsWithValues.length
        : undefined;

    return {
      name,
      movies,
      createdAt: new Date(),
      totalMovies: movies.length,
      totalRuntime,
      averageRating,
    };
  }

  /**
   * lista tutti generi disponibili
   */
  async getAllGenres(): Promise<string[]> {
    try {
      this.logger.log('recupero tutti i generi');

      const movies = await this.movieRepository.find();
      const genresSet = new Set<string>();

      movies.forEach((movie) => {
        movie.genres?.forEach((genre) => {
          if (genre) genresSet.add(genre);
        });
      });

      const genres = Array.from(genresSet).sort();

      this.logger.log(`${genres.length} generi trovati`);
      return genres;
    } catch (error) {
      this.logger.error(`errore recupero generi: ${error.message}`);
      throw error;
    }
  }

  /**
   * lista tutti registi disponibili
   */
  async getAllDirectors(): Promise<string[]> {
    try {
      this.logger.log('recupero tutti i registi');

      const movies = await this.movieRepository.find();
      const directorsSet = new Set<string>();

      movies.forEach((movie) => {
        if (movie.director) directorsSet.add(movie.director);
      });

      const directors = Array.from(directorsSet).sort();

      this.logger.log(`${directors.length} registi trovati`);
      return directors;
    } catch (error) {
      this.logger.error(`errore recupero registi: ${error.message}`);
      throw error;
    }
  }
}