//servizio analytics per generare statistiche avanzate

import { Injectable, Logger } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { MovieEntity } from '../../database/entities/movie.entity';

//interfacce analytics
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
  ) {}

  /**
   * statistiche base
   */
  async getBasicStats(): Promise<BasicStats> {
    try {
      this.logger.log('generazione statistiche base');

      const allMovies = await this.movieRepository.find();

      const watchedMovies = allMovies.filter((m) => m.is_watched);
      const enrichedMovies = allMovies.filter(
        (m) => m.tmdb_id && m.tmdb_id > 0,
      );

      //rating medio
      const ratingsWithValues = allMovies
        .map((m) => m.user_rating)
        .filter((r): r is number => r !== null && r !== undefined);

      const averageRating =
        ratingsWithValues.length > 0
          ? ratingsWithValues.reduce((a, b) => a + b, 0) /
            ratingsWithValues.length
          : undefined;

      //runtime totale
      const totalRuntime = watchedMovies
        .map((m) => m.runtime || 0)
        .reduce((a, b) => a + b, 0);

      //generi unici
      const allGenres = new Set(
        allMovies.flatMap((m) => m.genres || []).filter((g) => g),
      );

      //registi unici
      const allDirectors = new Set(
        allMovies.map((m) => m.director).filter((d) => d),
      );

      const stats: BasicStats = {
        totalMovies: allMovies.length,
        watchedMovies: watchedMovies.length,
        watchlistMovies: allMovies.length - watchedMovies.length,
        enrichedMovies: enrichedMovies.length,
        averageRating,
        totalRuntime,
        uniqueGenres: allGenres.size,
        uniqueDirectors: allDirectors.size,
      };

      this.logger.log(`stats generate: ${stats.totalMovies} film`);
      return stats;
    } catch (error) {
      this.logger.error(`errore stats base: ${error.message}`);
      throw error;
    }
  }

  /**
   * statistiche generi
   */
  async getGenreStats(limit: number = 10): Promise<GenreStats[]> {
    try {
      this.logger.log(`generazione stats generi (top ${limit})`);

      const allMovies = await this.movieRepository.find();
      const totalMovies = allMovies.length;

      //conta generi
      const genreCounts = new Map<string, number>();
      const genreRatings = new Map<string, number[]>();

      allMovies.forEach((movie) => {
        movie.genres?.forEach((genre) => {
          if (genre) {
            genreCounts.set(genre, (genreCounts.get(genre) || 0) + 1);

            if (movie.user_rating) {
              const ratings = genreRatings.get(genre) || [];
              ratings.push(movie.user_rating);
              genreRatings.set(genre, ratings);
            }
          }
        });
      });

      //converti in array e ordina
      const genreStats: GenreStats[] = Array.from(genreCounts.entries())
        .map(([genre, count]) => {
          const ratings = genreRatings.get(genre) || [];
          const averageRating =
            ratings.length > 0
              ? ratings.reduce((a, b) => a + b, 0) / ratings.length
              : undefined;

          return {
            genre,
            count,
            percentage: (count / totalMovies) * 100,
            averageRating,
          };
        })
        .sort((a, b) => b.count - a.count)
        .slice(0, limit);

      this.logger.log(`${genreStats.length} generi generati`);
      return genreStats;
    } catch (error) {
      this.logger.error(`errore stats generi: ${error.message}`);
      throw error;
    }
  }

  /**
   * statistiche anni
   */
  async getYearStats(): Promise<YearStats[]> {
    try {
      this.logger.log('generazione stats anni');

      const allMovies = await this.movieRepository.find();

      //filtra film con anno valido
      const moviesWithYear = allMovies.filter(
        (m) => m.year && m.year > 1900 && m.year <= new Date().getFullYear(),
      );

      //conta per anno
      const yearCounts = new Map<number, number>();
      const yearRatings = new Map<number, number[]>();

      moviesWithYear.forEach((movie) => {
        const year = movie.year!;
        yearCounts.set(year, (yearCounts.get(year) || 0) + 1);

        if (movie.user_rating) {
          const ratings = yearRatings.get(year) || [];
          ratings.push(movie.user_rating);
          yearRatings.set(year, ratings);
        }
      });

      //converti e ordina
      const yearStats: YearStats[] = Array.from(yearCounts.entries())
        .map(([year, count]) => {
          const ratings = yearRatings.get(year) || [];
          const averageRating =
            ratings.length > 0
              ? ratings.reduce((a, b) => a + b, 0) / ratings.length
              : undefined;

          return {
            year,
            count,
            averageRating,
          };
        })
        .sort((a, b) => a.year - b.year);

      this.logger.log(`${yearStats.length} anni generati`);
      return yearStats;
    } catch (error) {
      this.logger.error(`errore stats anni: ${error.message}`);
      throw error;
    }
  }

  /**
   * statistiche registi
   */
  async getDirectorStats(limit: number = 10): Promise<DirectorStats[]> {
    try {
      this.logger.log(`generazione stats registi (top ${limit})`);

      const allMovies = await this.movieRepository.find();

      //filtra film con regista
      const moviesWithDirector = allMovies.filter(
        (m) => m.director && m.director.trim(),
      );

      //conta per regista
      const directorCounts = new Map<string, number>();
      const directorRatings = new Map<string, number[]>();
      const directorRuntimes = new Map<string, number>();

      moviesWithDirector.forEach((movie) => {
        const director = movie.director!;
        directorCounts.set(director, (directorCounts.get(director) || 0) + 1);

        if (movie.user_rating) {
          const ratings = directorRatings.get(director) || [];
          ratings.push(movie.user_rating);
          directorRatings.set(director, ratings);
        }

        if (movie.runtime) {
          const currentRuntime = directorRuntimes.get(director) || 0;
          directorRuntimes.set(director, currentRuntime + movie.runtime);
        }
      });

      //converti e ordina
      const directorStats: DirectorStats[] = Array.from(
        directorCounts.entries(),
      )
        .map(([director, movieCount]) => {
          const ratings = directorRatings.get(director) || [];
          const averageRating =
            ratings.length > 0
              ? ratings.reduce((a, b) => a + b, 0) / ratings.length
              : undefined;

          return {
            director,
            movieCount,
            averageRating,
            totalRuntime: directorRuntimes.get(director) || 0,
          };
        })
        .sort((a, b) => b.movieCount - a.movieCount)
        .slice(0, limit);

      this.logger.log(`${directorStats.length} registi generati`);
      return directorStats;
    } catch (error) {
      this.logger.error(`errore stats registi: ${error.message}`);
      throw error;
    }
  }

  /**
   * distribuzione rating
   */
  async getRatingDistribution(): Promise<{ [key: string]: number }> {
    try {
      this.logger.log('generazione distribuzione rating');

      const allMovies = await this.movieRepository.find();
      const distribution: { [key: string]: number } = {};

      //inizializza categorie
      for (let i = 1; i <= 10; i++) {
        distribution[i.toString()] = 0;
      }

      //conta film per rating
      allMovies.forEach((movie) => {
        if (movie.user_rating) {
          const rating = Math.round(movie.user_rating);
          if (rating >= 1 && rating <= 10) {
            distribution[rating.toString()]++;
          }
        }
      });

      this.logger.log('distribuzione rating generata');
      return distribution;
    } catch (error) {
      this.logger.error(`errore distribuzione rating: ${error.message}`);
      throw error;
    }
  }

  /**
   * distribuzione decadi
   */
  async getDecadeDistribution(): Promise<{ [key: string]: number }> {
    try {
      this.logger.log('generazione distribuzione decadi');

      const allMovies = await this.movieRepository.find();
      const distribution: { [key: string]: number } = {};

      allMovies.forEach((movie) => {
        if (movie.year && movie.year >= 1900) {
          const decade = Math.floor(movie.year / 10) * 10;
          const key = `${decade}s`;
          distribution[key] = (distribution[key] || 0) + 1;
        }
      });

      this.logger.log('distribuzione decadi generata');
      return distribution;
    } catch (error) {
      this.logger.error(`errore distribuzione decadi: ${error.message}`);
      throw error;
    }
  }

  /**
   * analytics avanzate complete
   */
  async getAdvancedAnalytics(): Promise<AdvancedAnalytics> {
    try {
      this.logger.log('=== generazione analytics avanzate ===');

      const [
        basic,
        topGenres,
        moviesByYear,
        topDirectors,
        ratingDistribution,
        decadeDistribution,
      ] = await Promise.all([
        this.getBasicStats(),
        this.getGenreStats(10),
        this.getYearStats(),
        this.getDirectorStats(10),
        this.getRatingDistribution(),
        this.getDecadeDistribution(),
      ]);

      this.logger.log('✅ analytics avanzate complete');

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

  /**
   * report testuale dettagliato
   */
  async generateTextReport(): Promise<string> {
    try {
      const analytics = await this.getAdvancedAnalytics();

      const report = [
        '=== MOVIEAPP ANALYTICS REPORT ===',
        '',
        '📊 STATISTICHE BASE',
        `film totali: ${analytics.basic.totalMovies}`,
        `visti: ${analytics.basic.watchedMovies}`,
        `da vedere: ${analytics.basic.watchlistMovies}`,
        `arricchiti: ${analytics.basic.enrichedMovies}`,
        analytics.basic.averageRating
          ? `rating medio: ${analytics.basic.averageRating.toFixed(1)}/10`
          : '',
        `runtime totale: ${Math.round(analytics.basic.totalRuntime / 60)}h`,
        `generi unici: ${analytics.basic.uniqueGenres}`,
        `registi unici: ${analytics.basic.uniqueDirectors}`,
        '',
        '🎭 TOP 5 GENERI',
        ...analytics.topGenres.slice(0, 5).map((g, i) => {
          const rating = g.averageRating
            ? ` (avg: ${g.averageRating.toFixed(1)})`
            : '';
          return `${i + 1}. ${g.genre}: ${g.count} film (${g.percentage.toFixed(1)}%)${rating}`;
        }),
        '',
        '🎬 TOP 5 REGISTI',
        ...analytics.topDirectors.slice(0, 5).map((d, i) => {
          const rating = d.averageRating
            ? ` (avg: ${d.averageRating.toFixed(1)})`
            : '';
          return `${i + 1}. ${d.director}: ${d.movieCount} film${rating}`;
        }),
        '',
        '📅 DISTRIBUZIONE DECADI',
        ...Object.entries(analytics.decadeDistribution)
          .sort((a, b) => a[0].localeCompare(b[0]))
          .map(([decade, count]) => `${decade}: ${count} film`),
      ]
        .filter((line) => line !== '')
        .join('\n');

      this.logger.log('report testuale generato');
      return report;
    } catch (error) {
      this.logger.error(`errore report: ${error.message}`);
      throw error;
    }
  }
}