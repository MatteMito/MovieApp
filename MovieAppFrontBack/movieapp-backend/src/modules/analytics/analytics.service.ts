// File: src/modules/analytics/analytics.service.ts
// AGGIORNATO: usa UserMovieEntity per dati utente

import { Injectable, Logger } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { MovieEntity } from '../../database/entities/movie.entity';
import { UserMovieEntity, MovieStatus } from '../../database/entities/user-movie.entity';

export interface BasicStats {
  totalMovies: number;
  watchedMovies: number;
  watchlistMovies: number;
  enrichedMovies: number;
  averageRating?: number;
  totalRuntime: number;
  uniqueGenres: number;
  uniqueDirectors: number;
}

export interface GenreStats {
  genre: string;
  count: number;
  percentage: number;
  averageRating?: number;
}

export interface YearStats {
  year: number;
  count: number;
  averageRating?: number;
}

export interface DirectorStats {
  director: string;
  movieCount: number;
  averageRating?: number;
  totalRuntime: number;
}

export interface AdvancedAnalytics {
  basic: BasicStats;
  topGenres: GenreStats[];
  moviesByYear: YearStats[];
  topDirectors: DirectorStats[];
  ratingDistribution: { [key: string]: number };
  decadeDistribution: { [key: string]: number };
}

@Injectable()
export class AnalyticsService {
  private readonly logger = new Logger(AnalyticsService.name);

  constructor(
    @InjectRepository(MovieEntity)
    private movieRepository: Repository<MovieEntity>,
    
    @InjectRepository(UserMovieEntity)
    private userMovieRepository: Repository<UserMovieEntity>,
  ) {}

  /**
   * Statistiche base per un utente specifico
   */
  async getBasicStats(userId: string): Promise<BasicStats> {
    try {
      this.logger.log(`generazione statistiche base per utente ${userId}`);

      // Recupera tutti i film dell'utente con relazioni
      const userMovies = await this.userMovieRepository.find({
        where: { userId },
        relations: ['movie'],
      });

      const allMovies = userMovies.map(um => um.movie);
      const watchedMovies = userMovies.filter(um => um.status === MovieStatus.WATCHED);
      const enrichedMovies = allMovies.filter(m => m.tmdb_id && m.tmdb_id > 0);

      // Rating medio (dai dati utente)
      const ratingsWithValues = userMovies
        .map(um => um.userRating)
        .filter((r): r is number => r !== null && r !== undefined);

      const averageRating = ratingsWithValues.length > 0
        ? ratingsWithValues.reduce((a, b) => a + b, 0) / ratingsWithValues.length
        : undefined;

      // Runtime totale
      const totalRuntime = allMovies
        .map(m => m.runtime || 0)
        .reduce((a, b) => a + b, 0);

      // Generi unici
      const allGenres = new Set<string>();
      allMovies.forEach(m => {
        m.genres?.forEach(g => allGenres.add(g));
      });

      // Registi unici
      const allDirectors = new Set<string>();
      allMovies.forEach(m => {
        if (m.director) allDirectors.add(m.director);
      });

      return {
        totalMovies: allMovies.length,
        watchedMovies: watchedMovies.length,
        watchlistMovies: userMovies.filter(um => um.status === MovieStatus.WATCHLIST).length,
        enrichedMovies: enrichedMovies.length,
        averageRating,
        totalRuntime,
        uniqueGenres: allGenres.size,
        uniqueDirectors: allDirectors.size,
      };
    } catch (error) {
      this.logger.error(`errore statistiche base: ${error.message}`);
      throw error;
    }
  }

  /**
   * Statistiche per genere
   */
  async getGenreStats(userId: string): Promise<GenreStats[]> {
    try {
      const userMovies = await this.userMovieRepository.find({
        where: { userId },
        relations: ['movie'],
      });

      const genreMap = new Map<string, { count: number; ratings: number[] }>();

      userMovies.forEach(um => {
        um.movie.genres?.forEach(genre => {
          if (!genreMap.has(genre)) {
            genreMap.set(genre, { count: 0, ratings: [] });
          }
          const stats = genreMap.get(genre)!;
          stats.count++;
          if (um.userRating) {
            stats.ratings.push(um.userRating);
          }
        });
      });

      const totalMovies = userMovies.length;
      
      const genreStats: GenreStats[] = Array.from(genreMap.entries())
        .map(([genre, stats]) => ({
          genre,
          count: stats.count,
          percentage: (stats.count / totalMovies) * 100,
          averageRating: stats.ratings.length > 0
            ? stats.ratings.reduce((a, b) => a + b, 0) / stats.ratings.length
            : undefined,
        }))
        .sort((a, b) => b.count - a.count)
        .slice(0, 10);

      return genreStats;
    } catch (error) {
      this.logger.error(`errore statistiche generi: ${error.message}`);
      return [];
    }
  }

  /**
   * Statistiche per anno
   */
  async getYearStats(userId: string): Promise<YearStats[]> {
    try {
      const userMovies = await this.userMovieRepository.find({
        where: { userId },
        relations: ['movie'],
      });

      const yearMap = new Map<number, { count: number; ratings: number[] }>();

      userMovies.forEach(um => {
        const year = um.movie.year;
        if (!year) return;

        if (!yearMap.has(year)) {
          yearMap.set(year, { count: 0, ratings: [] });
        }
        const stats = yearMap.get(year)!;
        stats.count++;
        if (um.userRating) {
          stats.ratings.push(um.userRating);
        }
      });

      const yearStats: YearStats[] = Array.from(yearMap.entries())
        .map(([year, stats]) => ({
          year,
          count: stats.count,
          averageRating: stats.ratings.length > 0
            ? stats.ratings.reduce((a, b) => a + b, 0) / stats.ratings.length
            : undefined,
        }))
        .sort((a, b) => b.year - a.year);

      return yearStats;
    } catch (error) {
      this.logger.error(`errore statistiche anni: ${error.message}`);
      return [];
    }
  }

  /**
   * Statistiche per regista
   */
  async getDirectorStats(userId: string): Promise<DirectorStats[]> {
    try {
      const userMovies = await this.userMovieRepository.find({
        where: { userId },
        relations: ['movie'],
      });

      const directorMap = new Map<string, { count: number; ratings: number[]; runtime: number }>();

      userMovies.forEach(um => {
        const director = um.movie.director;
        if (!director) return;

        if (!directorMap.has(director)) {
          directorMap.set(director, { count: 0, ratings: [], runtime: 0 });
        }
        const stats = directorMap.get(director)!;
        stats.count++;
        if (um.userRating) {
          stats.ratings.push(um.userRating);
        }
        stats.runtime += um.movie.runtime || 0;
      });

      const directorStats: DirectorStats[] = Array.from(directorMap.entries())
        .map(([director, stats]) => ({
          director,
          movieCount: stats.count,
          averageRating: stats.ratings.length > 0
            ? stats.ratings.reduce((a, b) => a + b, 0) / stats.ratings.length
            : undefined,
          totalRuntime: stats.runtime,
        }))
        .sort((a, b) => b.movieCount - a.movieCount)
        .slice(0, 10);

      return directorStats;
    } catch (error) {
      this.logger.error(`errore statistiche registi: ${error.message}`);
      return [];
    }
  }

  /**
   * Analytics complete
   */
  async getAdvancedAnalytics(userId: string): Promise<AdvancedAnalytics> {
    try {
      this.logger.log(`generazione analytics avanzate per utente ${userId}`);

      const [basic, topGenres, moviesByYear, topDirectors] = await Promise.all([
        this.getBasicStats(userId),
        this.getGenreStats(userId),
        this.getYearStats(userId),
        this.getDirectorStats(userId),
      ]);

      // Rating distribution
      const userMovies = await this.userMovieRepository.find({
        where: { userId },
        relations: ['movie'],
      });

      const ratingDistribution: { [key: string]: number } = {};
      userMovies.forEach(um => {
        if (um.userRating) {
          const rating = Math.round(um.userRating);
          ratingDistribution[rating] = (ratingDistribution[rating] || 0) + 1;
        }
      });

      // Decade distribution
      const decadeDistribution: { [key: string]: number } = {};
      userMovies.forEach(um => {
        if (um.movie.year) {
          const decade = Math.floor(um.movie.year / 10) * 10;
          decadeDistribution[decade] = (decadeDistribution[decade] || 0) + 1;
        }
      });

      return {
        basic,
        topGenres,
        moviesByYear,
        topDirectors,
        ratingDistribution,
        decadeDistribution,
      };
    } catch (error) {
      this.logger.error(`errore analytics avanzate: ${error.message}`);
      throw error;
    }
  }
}