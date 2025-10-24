// File: src/modules/lists/lists.service.ts
// AGGIORNATO: usa UserMovieEntity per dati utente

import { Injectable, Logger } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { MovieEntity } from '../../database/entities/movie.entity';
import { UserMovieEntity, MovieStatus } from '../../database/entities/user-movie.entity';

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
  status?: 'watched' | 'watchlist';
  hasRating?: boolean;
  sortBy?: 'title' | 'year' | 'rating' | 'runtime';
  sortOrder?: 'ASC' | 'DESC';
  userId: string; // OBBLIGATORIO per filtrare i film dell'utente
}

@Injectable()
export class ListsService {
  private readonly logger = new Logger(ListsService.name);

  constructor(
    @InjectRepository(MovieEntity)
    private movieRepository: Repository<MovieEntity>,
    
    @InjectRepository(UserMovieEntity)
    private userMovieRepository: Repository<UserMovieEntity>,
  ) {}

  /**
   * Crea lista personalizzata con filtri per un utente specifico
   */
  async createCustomList(
    name: string,
    filters: ListFilters,
    description?: string,
  ): Promise<MovieList> {
    try {
      this.logger.log(`creazione lista personalizzata: ${name} per utente ${filters.userId}`);

      const movies = await this.filterMovies(filters);

      // Calcola statistiche lista
      const totalRuntime = movies
        .map((m) => m.runtime || 0)
        .reduce((a, b) => a + b, 0);

      // Per il rating medio, dobbiamo recuperare i dati da user_movies
      const movieIds = movies.map(m => m.id);
      const userMovies = await this.userMovieRepository.find({
        where: { 
          userId: filters.userId,
          movieId: movieIds as any, // TypeORM gestisce l'IN automaticamente
        },
      });

      const ratingsWithValues = userMovies
        .map(um => um.userRating || 0)
        .filter(r => r > 0);

      const averageRating = ratingsWithValues.length > 0
        ? ratingsWithValues.reduce((a, b) => a + b, 0) / ratingsWithValues.length
        : undefined;

      return {
        name,
        description,
        movies,
        createdAt: new Date(),
        totalMovies: movies.length,
        totalRuntime,
        averageRating,
      };
    } catch (error) {
      this.logger.error(`errore creazione lista: ${error.message}`);
      throw error;
    }
  }

  /**
   * Filtra film in base ai criteri specificati
   */
  private async filterMovies(filters: ListFilters): Promise<MovieEntity[]> {
    try {
      const { userId, status, ...movieFilters } = filters;

      // STEP 1: Recupera i film dell'utente con lo status richiesto
      const userMoviesQuery = this.userMovieRepository
        .createQueryBuilder('um')
        .leftJoinAndSelect('um.movie', 'movie')
        .where('um.userId = :userId', { userId });

      if (status) {
        const movieStatus = status === 'watched' ? MovieStatus.WATCHED : MovieStatus.WATCHLIST;
        userMoviesQuery.andWhere('um.status = :status', { status: movieStatus });
      }

      const userMovies = await userMoviesQuery.getMany();
      let movies = userMovies.map(um => um.movie);

      // STEP 2: Applica filtri sui film
      if (movieFilters.genre) {
        movies = movies.filter(m => 
          m.genres?.some(g => g.toLowerCase().includes(movieFilters.genre!.toLowerCase()))
        );
      }

      if (movieFilters.director) {
        movies = movies.filter(m => 
          m.director?.toLowerCase().includes(movieFilters.director!.toLowerCase())
        );
      }

      if (movieFilters.minYear) {
        movies = movies.filter(m => m.year && m.year >= movieFilters.minYear!);
      }

      if (movieFilters.maxYear) {
        movies = movies.filter(m => m.year && m.year <= movieFilters.maxYear!);
      }

      // Per i filtri sui rating, dobbiamo usare i dati di user_movies
      if (movieFilters.minRating || movieFilters.maxRating || movieFilters.hasRating) {
        const movieIds = movies.map(m => m.id);
        const userMoviesWithRatings = await this.userMovieRepository.find({
          where: { 
            userId,
            movieId: movieIds as any,
          },
        });

        const ratingMap = new Map(
          userMoviesWithRatings.map(um => [um.movieId, um.userRating])
        );

        movies = movies.filter(m => {
          const rating = ratingMap.get(m.id);
          
          if (movieFilters.hasRating && !rating) return false;
          if (movieFilters.minRating && (!rating || rating < movieFilters.minRating)) return false;
          if (movieFilters.maxRating && (!rating || rating > movieFilters.maxRating)) return false;
          
          return true;
        });
      }

      // STEP 3: Ordinamento
      if (movieFilters.sortBy) {
        movies = this.sortMovies(movies, movieFilters.sortBy, movieFilters.sortOrder || 'ASC');
      }

      return movies;
    } catch (error) {
      this.logger.error(`errore filtro film: ${error.message}`);
      return [];
    }
  }

  /**
   * Ordina film in base al criterio specificato
   */
  private sortMovies(
    movies: MovieEntity[],
    sortBy: string,
    sortOrder: 'ASC' | 'DESC',
  ): MovieEntity[] {
    const sorted = [...movies].sort((a, b) => {
      let comparison = 0;

      switch (sortBy) {
        case 'title':
          comparison = a.title.localeCompare(b.title);
          break;
        case 'year':
          comparison = (a.year || 0) - (b.year || 0);
          break;
        case 'runtime':
          comparison = (a.runtime || 0) - (b.runtime || 0);
          break;
        case 'rating':
          // Per il rating, useremmo tmdb_rating come fallback
          comparison = (a.tmdb_rating || 0) - (b.tmdb_rating || 0);
          break;
        default:
          comparison = 0;
      }

      return sortOrder === 'DESC' ? -comparison : comparison;
    });

    return sorted;
  }

  /**
   * Recupera le liste predefinite per un utente
   */
  async getPresetLists(userId: string): Promise<{
    topRated: MovieList;
    recentlyAdded: MovieList;
    longestMovies: MovieList;
  }> {
    try {
      const [topRated, recentlyAdded, longestMovies] = await Promise.all([
        this.createCustomList(
          'Top Rated',
          {
            userId,
            minRating: 8,
            sortBy: 'rating',
            sortOrder: 'DESC',
          },
          'I tuoi film con il rating più alto',
        ),
        this.createCustomList(
          'Recently Added',
          {
            userId,
            sortBy: 'year',
            sortOrder: 'DESC',
          },
          'Film aggiunti di recente',
        ),
        this.createCustomList(
          'Longest Movies',
          {
            userId,
            sortBy: 'runtime',
            sortOrder: 'DESC',
          },
          'I film più lunghi della tua collezione',
        ),
      ]);

      return { topRated, recentlyAdded, longestMovies };
    } catch (error) {
      this.logger.error(`errore recupero preset lists: ${error.message}`);
      throw error;
    }
  }
}