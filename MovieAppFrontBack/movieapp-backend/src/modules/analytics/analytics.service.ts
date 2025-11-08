// service per generazione statistiche avanzate
// calcola grafici per visualizzazione in android app
// statistiche: generi, registi, attori, decenni, paesi, rating, scatter plots

import { Injectable, Logger } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository, Not, IsNull } from 'typeorm';
import { MovieEntity } from '../../database/entities/movie.entity';
import { UserMovieEntity, MovieStatus } from '../../database/entities/user-movie.entity';

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
   * endpoint principale: genera tutte le statistiche in una chiamata
   * ritorna: basic stats, watched stats, rating stats
   */
  async getCompleteAnalytics(userId: string): Promise<any> {
    this.logger.log(`generazione analytics complete per utente ${userId}`);

    // recupera film watched con relazioni
    const watchedMovies = await this.userMovieRepository.find({
      where: { userId, status: MovieStatus.WATCHED },
      relations: ['movie'],
    });

    // recupera film rated (subset di watched con rating)
    const ratedMovies = await this.userMovieRepository.find({
      where: { 
        userId, 
        status: MovieStatus.WATCHED,
        userRating: Not(IsNull())
      },
      relations: ['movie'],
    });

    this.logger.log(`watched: ${watchedMovies.length}, rated: ${ratedMovies.length}`);

    // genera tutte le statistiche in parallelo per performance
    const [basicStats, watchedStats, ratingStats] = await Promise.all([
      this.generateBasicStats(userId, watchedMovies),
      this.generateWatchedStats(watchedMovies),
      this.generateRatingStats(ratedMovies),
    ]);

    return { basicStats, watchedStats, ratingStats };
  }

  // ===== statistiche base =====

  private async generateBasicStats(userId: string, watchedMovies: any[]): Promise<any> {
    // recupera tutti i film utente (watched + watchlist)
    const allUserMovies = await this.userMovieRepository.find({
      where: { userId },
      relations: ['movie'],
    });

    const watchlistMovies = allUserMovies.filter(um => um.status === MovieStatus.WATCHLIST);
    const enrichedMovies = allUserMovies.filter(um => um.movie?.tmdb_id);

    // calcola rating medio
    const watchedWithRating = watchedMovies.filter(um => um.userRating != null);
    const averageRating = watchedWithRating.length > 0
      ? watchedWithRating.reduce((sum, um) => sum + um.userRating, 0) / watchedWithRating.length
      : undefined;

    // calcola runtime totale
    const totalRuntime = watchedMovies.reduce((sum, um) => 
      sum + (um.movie?.runtime || 0), 0
    );

    // conta generi unici
    const genresSet = new Set<string>();
    watchedMovies.forEach(um => {
      um.movie?.genres?.forEach((g: string) => genresSet.add(g));
    });

    // conta registi unici
    const directorsSet = new Set<string>();
    watchedMovies.forEach(um => {
      if (um.movie?.director) directorsSet.add(um.movie.director);
    });

    return {
      totalMovies: allUserMovies.length,
      watchedCount: watchedMovies.length,
      watchlistCount: watchlistMovies.length,
      enrichedMovies: enrichedMovies.length,
      averageRating: averageRating ? Math.round(averageRating * 10) / 10 : undefined,
      totalRuntime,
      uniqueGenres: genresSet.size,
      uniqueDirectors: directorsSet.size,
    };
  }

  // ===== statistiche watched (grafici principali) =====

  private async generateWatchedStats(watchedMovies: any[]): Promise<any> {
    // 1. distribuzione generi (pie chart)
    const genresDistribution = this.calculateGenresDistribution(watchedMovies);

    // 2. combinazioni generi (bar chart)
    const genreCombinations = this.calculateGenreCombinations(watchedMovies);

    // 3. distribuzione decenni (bar chart)
    const decadeDistribution = this.calculateDecadeDistribution(watchedMovies);

    // 4. top 10 registi (bar chart)
    const topDirectors = this.calculateTopDirectors(watchedMovies);

    // 5. top 10 attori (bar chart)
    const topActors = this.calculateTopActors(watchedMovies);

    // 6. coppie regista-attore (bar chart)
    const directorActorPairs = this.calculateDirectorActorPairs(watchedMovies);

    // 7. studi produzione (bar chart)
    const productionStudios = this.calculateProductionStudios(watchedMovies);

    // 8. paesi produzione (bar chart)
    const productionCountries = this.calculateProductionCountries(watchedMovies);

    // 9. distribuzione continenti (pie chart)
    const continentDistribution = this.calculateContinentDistribution(watchedMovies);

    return {
      genresDistribution,
      genreCombinations,
      decadeDistribution,
      topDirectors,
      topActors,
      directorActorPairs,
      topWriters: [], // placeholder
      topComposers: [], // placeholder
      topCinematographers: [], // placeholder
      productionStudios,
      productionCountries,
      continentDistribution,
    };
  }

  // ===== statistiche rating (grafici avanzati) =====

  private async generateRatingStats(ratedMovies: any[]): Promise<any> {
    // 1. distribuzione rating utente (bar chart)
    const userRatingsDistribution = this.calculateRatingsDistribution(
      ratedMovies.map(um => um.userRating)
    );

    // 2. distribuzione rating tmdb (bar chart)
    const communityRatingsDistribution = this.calculateRatingsDistribution(
      ratedMovies.map(um => um.movie?.tmdb_rating).filter(r => r)
    );

    // 3. opinioni divergenti (scatter plot)
    const divergentOpinions = this.calculateDivergentOpinions(ratedMovies);

    // 4. rating medio per regista (bar chart)
    const avgRatingByDirector = this.calculateAvgRatingByDirector(ratedMovies);

    // 5. rating medio per attore (bar chart)
    const avgRatingByActor = this.calculateAvgRatingByActor(ratedMovies);

    // 6. rating medio per genere (bar chart)
    const avgRatingByGenre = this.calculateAvgRatingByGenre(ratedMovies);

    // 7. rating medio per decade (line chart)
    const avgRatingByDecade = this.calculateAvgRatingByDecade(ratedMovies);

    // 8. runtime vs rating (scatter plot)
    const runtimeVsRating = this.calculateRuntimeVsRating(ratedMovies);

    // 9. revenue vs rating (scatter plot)
    const revenueVsRating = this.calculateRevenueVsRating(ratedMovies);

    return {
      userRatingsDistribution,
      communityRatingsDistribution,
      divergentOpinions,
      avgRatingByDirector,
      avgRatingByActor,
      avgRatingByGenre,
      avgRatingByGenreCombination: [], // placeholder
      avgRatingByDecade,
      runtimeVsRating,
      revenueVsRating,
    };
  }

  // ===== metodi calcolo specifici =====

  private calculateGenresDistribution(movies: any[]): any[] {
    const genreMap = new Map<string, number>();
    const total = movies.length;

    movies.forEach(um => {
      um.movie?.genres?.forEach((genre: string) => {
        genreMap.set(genre, (genreMap.get(genre) || 0) + 1);
      });
    });

    return Array.from(genreMap.entries())
      .map(([genre, count]) => ({
        genre,
        count,
        percentage: Math.round((count / total) * 100 * 10) / 10,
      }))
      .sort((a, b) => b.count - a.count)
      .slice(0, 10);
  }

  private calculateGenreCombinations(movies: any[]): any {
    const comboMap = new Map<string, number>();

    movies.forEach(um => {
      const genres = um.movie?.genres;
      if (genres && genres.length > 1) {
        const combo = genres.slice().sort().join(', ');
        comboMap.set(combo, (comboMap.get(combo) || 0) + 1);
      }
    });

    return Object.fromEntries(
      Array.from(comboMap.entries())
        .sort((a, b) => b[1] - a[1])
        .slice(0, 10)
    );
  }

  private calculateDecadeDistribution(movies: any[]): any {
    const decadeMap = new Map<string, number>();

    movies.forEach(um => {
      const year = um.movie?.year;
      if (year) {
        const decade = `${Math.floor(year / 10) * 10}s`;
        decadeMap.set(decade, (decadeMap.get(decade) || 0) + 1);
      }
    });

    return Object.fromEntries(
      Array.from(decadeMap.entries()).sort((a, b) => a[0].localeCompare(b[0]))
    );
  }

  private calculateTopDirectors(movies: any[]): any[] {
    const directorMap = new Map<string, number>();

    movies.forEach(um => {
      if (um.movie?.director) {
        directorMap.set(um.movie.director, (directorMap.get(um.movie.director) || 0) + 1);
      }
    });

    return Array.from(directorMap.entries())
      .map(([director, movieCount]) => ({ director, movieCount }))
      .sort((a, b) => b.movieCount - a.movieCount)
      .slice(0, 10);
  }

  private calculateTopActors(movies: any[]): any[] {
    const actorMap = new Map<string, number>();

    movies.forEach(um => {
      um.movie?.actors?.forEach((actor: string) => {
        actorMap.set(actor, (actorMap.get(actor) || 0) + 1);
      });
    });

    return Array.from(actorMap.entries())
      .map(([actor, movieCount]) => ({ actor, movieCount }))
      .sort((a, b) => b.movieCount - a.movieCount)
      .slice(0, 10);
  }

  private calculateDirectorActorPairs(movies: any[]): any[] {
    const pairMap = new Map<string, number>();

    movies.forEach(um => {
      if (um.movie?.director && um.movie?.actors) {
        um.movie.actors.forEach((actor: string) => {
          const pair = `${um.movie.director}|${actor}`;
          pairMap.set(pair, (pairMap.get(pair) || 0) + 1);
        });
      }
    });

    return Array.from(pairMap.entries())
      .map(([pair, count]) => {
        const [director, actor] = pair.split('|');
        return { director, actor, movieCount: count };
      })
      .sort((a, b) => b.movieCount - a.movieCount)
      .slice(0, 10);
  }

  private calculateProductionStudios(movies: any[]): any[] {
    const studioMap = new Map<string, number>();

    movies.forEach(um => {
      um.movie?.production_companies?.forEach((studio: string) => {
        studioMap.set(studio, (studioMap.get(studio) || 0) + 1);
      });
    });

    return Array.from(studioMap.entries())
      .map(([studio, count]) => ({ studio, count }))
      .sort((a, b) => b.count - a.count)
      .slice(0, 10);
  }

  private calculateProductionCountries(movies: any[]): any[] {
    const countryMap = new Map<string, number>();
    const total = movies.length;

    movies.forEach(um => {
      um.movie?.production_countries?.forEach((country: string) => {
        countryMap.set(country, (countryMap.get(country) || 0) + 1);
      });
    });

    return Array.from(countryMap.entries())
      .map(([country, count]) => ({
        country,
        count,
        percentage: Math.round((count / total) * 100 * 10) / 10,
      }))
      .sort((a, b) => b.count - a.count);
  }

  private calculateContinentDistribution(movies: any[]): any {
    // mappatura paesi a continenti (semplificata)
    const continentMap: any = {
      'United States': 'North America',
      'Canada': 'North America',
      'United Kingdom': 'Europe',
      'France': 'Europe',
      'Germany': 'Europe',
      'Italy': 'Europe',
      'Spain': 'Europe',
      'Japan': 'Asia',
      'South Korea': 'Asia',
      'China': 'Asia',
      'India': 'Asia',
      'Australia': 'Oceania',
    };

    const distribution: any = {
      'North America': 0,
      'Europe': 0,
      'Asia': 0,
      'South America': 0,
      'Africa': 0,
      'Oceania': 0,
    };

    movies.forEach(um => {
      um.movie?.production_countries?.forEach((country: string) => {
        const continent = continentMap[country] || 'Other';
        if (distribution[continent] !== undefined) {
          distribution[continent]++;
        }
      });
    });

    return distribution;
  }

  private calculateRatingsDistribution(ratings: number[]): any {
    const dist: any = {};
    
    for (let i = 1; i <= 10; i++) {
      dist[i.toString()] = 0;
    }

    ratings.forEach(rating => {
      const rounded = Math.round(rating);
      if (rounded >= 1 && rounded <= 10) {
        dist[rounded.toString()]++;
      }
    });

    return dist;
  }

  private calculateDivergentOpinions(ratedMovies: any[]): any[] {
    return ratedMovies
      .filter(um => um.movie?.tmdb_rating)
      .map(um => ({
        movieTitle: um.movie.title,
        userRating: um.userRating,
        tmdbRating: um.movie.tmdb_rating,
        difference: Math.abs(um.userRating - um.movie.tmdb_rating),
      }))
      .sort((a, b) => b.difference - a.difference)
      .slice(0, 10);
  }

  private calculateAvgRatingByDirector(ratedMovies: any[]): any[] {
    const directorMap = new Map<string, number[]>();

    ratedMovies.forEach(um => {
      if (um.movie?.director) {
        const ratings = directorMap.get(um.movie.director) || [];
        ratings.push(um.userRating);
        directorMap.set(um.movie.director, ratings);
      }
    });

    return Array.from(directorMap.entries())
      .filter(([_, ratings]) => ratings.length >= 2)
      .map(([director, ratings]) => ({
        director,
        avgRating: Math.round((ratings.reduce((a, b) => a + b) / ratings.length) * 10) / 10,
      }))
      .sort((a, b) => b.avgRating - a.avgRating)
      .slice(0, 10);
  }

  private calculateAvgRatingByActor(ratedMovies: any[]): any[] {
    const actorMap = new Map<string, number[]>();

    ratedMovies.forEach(um => {
      um.movie?.actors?.forEach((actor: string) => {
        const ratings = actorMap.get(actor) || [];
        ratings.push(um.userRating);
        actorMap.set(actor, ratings);
      });
    });

    return Array.from(actorMap.entries())
      .filter(([_, ratings]) => ratings.length >= 2)
      .map(([actor, ratings]) => ({
        actor,
        avgRating: Math.round((ratings.reduce((a, b) => a + b) / ratings.length) * 10) / 10,
      }))
      .sort((a, b) => b.avgRating - a.avgRating)
      .slice(0, 10);
  }

  private calculateAvgRatingByGenre(ratedMovies: any[]): any[] {
    const genreMap = new Map<string, number[]>();

    ratedMovies.forEach(um => {
      um.movie?.genres?.forEach((genre: string) => {
        const ratings = genreMap.get(genre) || [];
        ratings.push(um.userRating);
        genreMap.set(genre, ratings);
      });
    });

    return Array.from(genreMap.entries())
      .map(([genre, ratings]) => ({
        genre,
        avgRating: Math.round((ratings.reduce((a, b) => a + b) / ratings.length) * 10) / 10,
      }))
      .sort((a, b) => b.avgRating - a.avgRating);
  }

  private calculateAvgRatingByDecade(ratedMovies: any[]): any {
    const decadeMap = new Map<string, number[]>();

    ratedMovies.forEach(um => {
      const year = um.movie?.year;
      if (year) {
        const decade = `${Math.floor(year / 10) * 10}s`;
        const ratings = decadeMap.get(decade) || [];
        ratings.push(um.userRating);
        decadeMap.set(decade, ratings);
      }
    });

    const result: any = {};
    decadeMap.forEach((ratings, decade) => {
      result[decade] = Math.round((ratings.reduce((a, b) => a + b) / ratings.length) * 10) / 10;
    });

    return result;
  }

  private calculateRuntimeVsRating(ratedMovies: any[]): any[] {
    return ratedMovies
      .filter(um => um.movie?.runtime)
      .map(um => ({
        runtime: um.movie.runtime,
        rating: um.userRating,
      }));
  }

  private calculateRevenueVsRating(ratedMovies: any[]): any[] {
    return ratedMovies
      .filter(um => um.movie?.revenue && um.movie.revenue > 0)
      .map(um => ({
        revenue: um.movie.revenue,
        rating: um.userRating,
      }));
  }

  // ===== metodi helper pubblici =====

  async getBasicStats(userId: string): Promise<any> {
    const watchedMovies = await this.userMovieRepository.find({
      where: { userId, status: MovieStatus.WATCHED },
      relations: ['movie'],
    });
    return this.generateBasicStats(userId, watchedMovies);
  }

  async getGenreStats(userId: string): Promise<any> {
    const watchedMovies = await this.userMovieRepository.find({
      where: { userId, status: MovieStatus.WATCHED },
      relations: ['movie'],
    });
    return this.calculateGenresDistribution(watchedMovies);
  }

  async getDirectorStats(userId: string): Promise<any> {
    const watchedMovies = await this.userMovieRepository.find({
      where: { userId, status: MovieStatus.WATCHED },
      relations: ['movie'],
    });
    return this.calculateTopDirectors(watchedMovies);
  }

  async getAdvancedAnalytics(userId: string): Promise<any> {
    return this.getCompleteAnalytics(userId);
  }
}