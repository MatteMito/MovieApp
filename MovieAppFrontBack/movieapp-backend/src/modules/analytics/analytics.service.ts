// service completo con tutte le statistiche utente

import { Injectable, Logger } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository, Not, IsNull } from 'typeorm';
import { MovieEntity } from '../../database/entities/movie.entity';
import { UserMovieEntity, MovieStatus } from '../../database/entities/user-movie.entity';

// statistiche base: totali, medie, conteggi
export interface BasicStats {
  totalMovies: number;
  watchedCount: number;
  watchlistCount: number;
  enrichedMovies: number;
  averageRating?: number;
  totalRuntime: number;
  uniqueGenres: number;
  uniqueDirectors: number;
}

// statistiche per genere con conteggi e percentuali
export interface GenreStats {
  genre: string;
  count: number;
  percentage: number;
  averageRating?: number;
}

// statistiche per anno
export interface YearStats {
  year: number;
  count: number;
  averageRating?: number;
}

// statistiche per regista con medie e runtime
export interface DirectorStats {
  director: string;
  movieCount: number;
  averageRating?: number;
  totalRuntime: number;
}

// statistiche per attore
export interface ActorStats {
  actor: string;
  movieCount: number;
  averageRating?: number;
}

// coppie regista-attore ricorrenti
export interface DirectorActorPair {
  director: string;
  actor: string;
  movieCount: number;
}

// statistiche per paese di produzione
export interface CountryStats {
  country: string;
  count: number;
  percentage: number;
}

// statistiche per studio di produzione
export interface StudioStats {
  studio: string;
  count: number;
}

// film con rating divergenti rispetto a tmdb
export interface DivergentOpinion {
  movieTitle: string;
  userRating: number;
  tmdbRating: number;
  difference: number;
}

// risposta completa endpoint principale
export interface CompleteAnalytics {
  basicStats: BasicStats;
  watchedStats: {
    genresDistribution: GenreStats[];
    genreCombinations: { [key: string]: number };
    decadeDistribution: { [key: string]: number };
    topDirectors: DirectorStats[];
    topActors: ActorStats[];
    directorActorPairs: DirectorActorPair[];
    topWriters: any[];
    topComposers: any[];
    topCinematographers: any[];
    productionStudios: StudioStats[];
    productionCountries: CountryStats[];
    continentDistribution: { [key: string]: number };
  };
  ratingStats: {
    userRatingsDistribution: { [key: string]: number };
    communityRatingsDistribution: { [key: string]: number };
    divergentOpinions: DivergentOpinion[];
    avgRatingByDirector: { director: string; avgRating: number }[];
    avgRatingByActor: { actor: string; avgRating: number }[];
    avgRatingByGenre: { genre: string; avgRating: number }[];
    avgRatingByGenreCombination: { combination: string; avgRating: number }[];
    avgRatingByDecade: { [key: string]: number };
    runtimeVsRating: { runtime: number; rating: number }[];
    revenueVsRating: { revenue: number; rating: number }[];
  };
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

  // endpoint principale: ritorna tutte le statistiche in una chiamata
  async getCompleteAnalytics(userId: string): Promise<CompleteAnalytics> {
    this.logger.log(`generazione analytics complete per utente ${userId}`);

    // recupera film watched e rated dell'utente
    const watchedMovies = await this.userMovieRepository.find({
      where: { userId, status: MovieStatus.WATCHED },
      relations: ['movie'], // include dati film da movies table
    });

    const ratedMovies = await this.userMovieRepository.find({
      where: { 
        userId, 
        status: MovieStatus.WATCHED,
        userRating: Not(IsNull()) // solo film con voto
      },
      relations: ['movie'],
    });

    this.logger.log(`watched: ${watchedMovies.length}, rated: ${ratedMovies.length}`);

    // genera tutte le statistiche in parallelo per performance
    const [
      basicStats,
      watchedStats,
      ratingStats
    ] = await Promise.all([
      this.generateBasicStats(userId, watchedMovies),
      this.generateWatchedStats(watchedMovies),
      this.generateRatingStats(ratedMovies),
    ]);

    return {
      basicStats,
      watchedStats,
      ratingStats,
    };
  }

  // genera statistiche base: totali, medie, conteggi
  private async generateBasicStats(userId: string, watchedMovies: UserMovieEntity[]): Promise<BasicStats> {
    // recupera tutti i film dell'utente (watched + watchlist)
    const allUserMovies = await this.userMovieRepository.find({
      where: { userId },
      relations: ['movie'],
    });

    const watchlistMovies = allUserMovies.filter(um => um.status === MovieStatus.WATCHLIST);
    const allMovies = allUserMovies.map(um => um.movie);
    const enrichedMovies = allMovies.filter(m => m.tmdb_id && m.tmdb_id > 0);

    // calcola media rating solo per film con voto
    const watchedWithRating = watchedMovies.filter(um => um.userRating != null);
    const averageRating = watchedWithRating.length > 0
      ? watchedWithRating.reduce((sum, um) => sum + um.userRating!, 0) / watchedWithRating.length
      : undefined;

    // calcola runtime totale
    const totalRuntime = watchedMovies
      .map(um => um.movie.runtime || 0)
      .reduce((a, b) => a + b, 0);

    // conta generi unici
    const allGenres = new Set<string>();
    watchedMovies.forEach(um => {
      um.movie.genres?.forEach(g => allGenres.add(g));
    });

    // conta registi unici
    const allDirectors = new Set<string>();
    watchedMovies.forEach(um => {
      if (um.movie.director) allDirectors.add(um.movie.director);
    });

    return {
      totalMovies: allUserMovies.length,
      watchedCount: watchedMovies.length,
      watchlistCount: watchlistMovies.length,
      enrichedMovies: enrichedMovies.length,
      averageRating: averageRating ? parseFloat(averageRating.toFixed(2)) : undefined,
      totalRuntime,
      uniqueGenres: allGenres.size,
      uniqueDirectors: allDirectors.size,
    };
  }

  // genera statistiche basate su film watched (anche senza rating)
  private async generateWatchedStats(watchedMovies: UserMovieEntity[]) {
    const totalWatched = watchedMovies.length;

    // distribuzione generi con conteggi e percentuali
    const genreMap = new Map<string, { count: number; ratings: number[] }>();
    watchedMovies.forEach(um => {
      um.movie.genres?.forEach(genre => {
        if (!genreMap.has(genre)) {
          genreMap.set(genre, { count: 0, ratings: [] });
        }
        const stats = genreMap.get(genre)!;
        stats.count++;
        if (um.userRating) stats.ratings.push(um.userRating);
      });
    });

    const genresDistribution: GenreStats[] = Array.from(genreMap.entries())
      .map(([genre, stats]) => ({
        genre,
        count: stats.count,
        percentage: (stats.count / totalWatched) * 100,
        averageRating: stats.ratings.length > 0
          ? stats.ratings.reduce((a, b) => a + b) / stats.ratings.length
          : undefined,
      }))
      .sort((a, b) => b.count - a.count);

    // combinazioni di generi (es: Action + Comedy)
    const genreCombinations: { [key: string]: number } = {};
    watchedMovies.forEach(um => {
      const genres = um.movie.genres || [];
      if (genres.length >= 2) {
        // genera tutte le coppie possibili
        for (let i = 0; i < genres.length; i++) {
          for (let j = i + 1; j < genres.length; j++) {
            const combo = [genres[i], genres[j]].sort().join(' + ');
            genreCombinations[combo] = (genreCombinations[combo] || 0) + 1;
          }
        }
      }
    });

    // distribuzione per decennio
    const decadeDistribution: { [key: string]: number } = {};
    watchedMovies.forEach(um => {
      if (um.movie.year) {
        const decade = `${Math.floor(um.movie.year / 10) * 10}s`;
        decadeDistribution[decade] = (decadeDistribution[decade] || 0) + 1;
      }
    });

    // top registi con conteggio, rating medio e runtime totale
    const directorMap = new Map<string, { count: number; ratings: number[]; runtime: number }>();
    watchedMovies.forEach(um => {
      if (um.movie.director) {
        if (!directorMap.has(um.movie.director)) {
          directorMap.set(um.movie.director, { count: 0, ratings: [], runtime: 0 });
        }
        const stats = directorMap.get(um.movie.director)!;
        stats.count++;
        if (um.userRating) stats.ratings.push(um.userRating);
        stats.runtime += um.movie.runtime || 0;
      }
    });

    const topDirectors: DirectorStats[] = Array.from(directorMap.entries())
      .map(([director, stats]) => ({
        director,
        movieCount: stats.count,
        averageRating: stats.ratings.length > 0
          ? stats.ratings.reduce((a, b) => a + b) / stats.ratings.length
          : undefined,
        totalRuntime: stats.runtime,
      }))
      .sort((a, b) => b.movieCount - a.movieCount)
      .slice(0, 10);

    // top attori con conteggio e rating medio
    const actorMap = new Map<string, { count: number; ratings: number[] }>();
    watchedMovies.forEach(um => {
      um.movie.actors?.forEach(actor => {
        if (!actorMap.has(actor)) {
          actorMap.set(actor, { count: 0, ratings: [] });
        }
        const stats = actorMap.get(actor)!;
        stats.count++;
        if (um.userRating) stats.ratings.push(um.userRating);
      });
    });

    const topActors: ActorStats[] = Array.from(actorMap.entries())
      .map(([actor, stats]) => ({
        actor,
        movieCount: stats.count,
        averageRating: stats.ratings.length > 0
          ? stats.ratings.reduce((a, b) => a + b) / stats.ratings.length
          : undefined,
      }))
      .sort((a, b) => b.movieCount - a.movieCount)
      .slice(0, 10);

    // coppie regista-attore ricorrenti
    const pairMap = new Map<string, number>();
    watchedMovies.forEach(um => {
      if (um.movie.director && um.movie.actors) {
        um.movie.actors.forEach(actor => {
          const pair = `${um.movie.director}|${actor}`;
          pairMap.set(pair, (pairMap.get(pair) || 0) + 1);
        });
      }
    });

    const directorActorPairs: DirectorActorPair[] = Array.from(pairMap.entries())
      .map(([pair, count]) => {
        const [director, actor] = pair.split('|');
        return { director, actor, movieCount: count };
      })
      .sort((a, b) => b.movieCount - a.movieCount)
      .slice(0, 10);

    // studi di produzione più frequenti
    const studioMap = new Map<string, number>();
    watchedMovies.forEach(um => {
      um.movie.production_companies?.forEach(studio => {
        studioMap.set(studio, (studioMap.get(studio) || 0) + 1);
      });
    });

    const productionStudios: StudioStats[] = Array.from(studioMap.entries())
      .map(([studio, count]) => ({ studio, count }))
      .sort((a, b) => b.count - a.count)
      .slice(0, 10);

    // paesi di produzione con percentuali
    const countryMap = new Map<string, number>();
    watchedMovies.forEach(um => {
      um.movie.production_countries?.forEach(country => {
        countryMap.set(country, (countryMap.get(country) || 0) + 1);
      });
    });

    const productionCountries: CountryStats[] = Array.from(countryMap.entries())
      .map(([country, count]) => ({
        country,
        count,
        percentage: (count / totalWatched) * 100,
      }))
      .sort((a, b) => b.count - a.count);

    // distribuzione per continente (mapping semplificato)
    const continentDistribution: { [key: string]: number } = {
      'North America': 0,
      'Europe': 0,
      'Asia': 0,
      'South America': 0,
      'Africa': 0,
      'Oceania': 0,
    };
    
    // mappatura paesi a continenti (semplificata)
    const continentMap: { [key: string]: string } = {
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

    watchedMovies.forEach(um => {
      um.movie.production_countries?.forEach(country => {
        const continent = continentMap[country] || 'Other';
        if (continentDistribution[continent] !== undefined) {
          continentDistribution[continent]++;
        }
      });
    });

    return {
      genresDistribution,
      genreCombinations,
      decadeDistribution,
      topDirectors,
      topActors,
      directorActorPairs,
      topWriters: [], // todo se disponibile nei dati
      topComposers: [], // todo se disponibile nei dati
      topCinematographers: [], // todo se disponibile nei dati
      productionStudios,
      productionCountries,
      continentDistribution,
    };
  }

  // genera statistiche basate sui rating utente
  private async generateRatingStats(ratedMovies: UserMovieEntity[]) {
    // se nessun film con rating, ritorna struttura vuota
    if (ratedMovies.length === 0) {
      return {
        userRatingsDistribution: {},
        communityRatingsDistribution: {},
        divergentOpinions: [],
        avgRatingByDirector: [],
        avgRatingByActor: [],
        avgRatingByGenre: [],
        avgRatingByGenreCombination: [],
        avgRatingByDecade: {},
        runtimeVsRating: [],
        revenueVsRating: [],
      };
    }

    // distribuzione rating utente (0-10)
    const userRatingsDistribution: { [key: string]: number } = {};
    ratedMovies.forEach(um => {
      const rating = Math.round(um.userRating!);
      userRatingsDistribution[rating] = (userRatingsDistribution[rating] || 0) + 1;
    });

    // distribuzione rating community (tmdb)
    const communityRatingsDistribution: { [key: string]: number } = {};
    ratedMovies.forEach(um => {
      if (um.movie.tmdb_rating) {
        const rating = Math.round(um.movie.tmdb_rating);
        communityRatingsDistribution[rating] = (communityRatingsDistribution[rating] || 0) + 1;
      }
    });

    // opinioni divergenti (differenza >= 2 punti con tmdb)
    const divergentOpinions: DivergentOpinion[] = ratedMovies
      .filter(um => um.movie.tmdb_rating && Math.abs(um.userRating! - um.movie.tmdb_rating) >= 2)
      .map(um => ({
        movieTitle: um.movie.title,
        userRating: um.userRating!,
        tmdbRating: um.movie.tmdb_rating!,
        difference: um.userRating! - um.movie.tmdb_rating!,
      }))
      .sort((a, b) => Math.abs(b.difference) - Math.abs(a.difference))
      .slice(0, 20);

    // rating medio per regista (minimo 3 film)
    const directorRatings = new Map<string, number[]>();
    ratedMovies.forEach(um => {
      if (um.movie.director) {
        if (!directorRatings.has(um.movie.director)) {
          directorRatings.set(um.movie.director, []);
        }
        directorRatings.get(um.movie.director)!.push(um.userRating!);
      }
    });

    const avgRatingByDirector = Array.from(directorRatings.entries())
      .filter(([_, ratings]) => ratings.length >= 3) // solo registi con almeno 3 film
      .map(([director, ratings]) => ({
        director,
        avgRating: ratings.reduce((a, b) => a + b) / ratings.length,
      }))
      .sort((a, b) => b.avgRating - a.avgRating)
      .slice(0, 10);

    // rating medio per attore (minimo 3 film)
    const actorRatings = new Map<string, number[]>();
    ratedMovies.forEach(um => {
      um.movie.actors?.forEach(actor => {
        if (!actorRatings.has(actor)) {
          actorRatings.set(actor, []);
        }
        actorRatings.get(actor)!.push(um.userRating!);
      });
    });

    const avgRatingByActor = Array.from(actorRatings.entries())
      .filter(([_, ratings]) => ratings.length >= 3) // solo attori con almeno 3 film
      .map(([actor, ratings]) => ({
        actor,
        avgRating: ratings.reduce((a, b) => a + b) / ratings.length,
      }))
      .sort((a, b) => b.avgRating - a.avgRating)
      .slice(0, 10);

    // rating medio per genere
    const genreRatings = new Map<string, number[]>();
    ratedMovies.forEach(um => {
      um.movie.genres?.forEach(genre => {
        if (!genreRatings.has(genre)) {
          genreRatings.set(genre, []);
        }
        genreRatings.get(genre)!.push(um.userRating!);
      });
    });

    const avgRatingByGenre = Array.from(genreRatings.entries())
      .map(([genre, ratings]) => ({
        genre,
        avgRating: ratings.reduce((a, b) => a + b) / ratings.length,
      }))
      .sort((a, b) => b.avgRating - a.avgRating);

    // rating medio per combinazione generi (minimo 3 film)
    const comboRatings = new Map<string, number[]>();
    ratedMovies.forEach(um => {
      const genres = um.movie.genres || [];
      if (genres.length >= 2) {
        for (let i = 0; i < genres.length; i++) {
          for (let j = i + 1; j < genres.length; j++) {
            const combo = [genres[i], genres[j]].sort().join(' + ');
            if (!comboRatings.has(combo)) {
              comboRatings.set(combo, []);
            }
            comboRatings.get(combo)!.push(um.userRating!);
          }
        }
      }
    });

    const avgRatingByGenreCombination = Array.from(comboRatings.entries())
      .filter(([_, ratings]) => ratings.length >= 3) // solo combinazioni con almeno 3 film
      .map(([combination, ratings]) => ({
        combination,
        avgRating: ratings.reduce((a, b) => a + b) / ratings.length,
      }))
      .sort((a, b) => b.avgRating - a.avgRating)
      .slice(0, 10);

    // rating medio per decennio
    const decadeRatings = new Map<string, number[]>();
    ratedMovies.forEach(um => {
      if (um.movie.year) {
        const decade = `${Math.floor(um.movie.year / 10) * 10}s`;
        if (!decadeRatings.has(decade)) {
          decadeRatings.set(decade, []);
        }
        decadeRatings.get(decade)!.push(um.userRating!);
      }
    });

    const avgRatingByDecade: { [key: string]: number } = {};
    decadeRatings.forEach((ratings, decade) => {
      avgRatingByDecade[decade] = ratings.reduce((a, b) => a + b) / ratings.length;
    });

    // correlazione durata vs rating
    const runtimeVsRating = ratedMovies
      .filter(um => um.movie.runtime)
      .map(um => ({
        runtime: um.movie.runtime!,
        rating: um.userRating!,
      }));

    // correlazione incasso vs rating
    const revenueVsRating = ratedMovies
      .filter(um => um.movie.revenue && um.movie.revenue > 0)
      .map(um => ({
        revenue: um.movie.revenue!,
        rating: um.userRating!,
      }));

    return {
      userRatingsDistribution,
      communityRatingsDistribution,
      divergentOpinions,
      avgRatingByDirector,
      avgRatingByActor,
      avgRatingByGenre,
      avgRatingByGenreCombination,
      avgRatingByDecade,
      runtimeVsRating,
      revenueVsRating,
    };
  }

  // ritorna solo statistiche base
  async getBasicStats(userId: string): Promise<BasicStats> {
    const watchedMovies = await this.userMovieRepository.find({
      where: { userId, status: MovieStatus.WATCHED },
      relations: ['movie'],
    });
    return this.generateBasicStats(userId, watchedMovies);
  }

  // ritorna solo statistiche generi
  async getGenreStats(userId: string): Promise<GenreStats[]> {
    const watchedMovies = await this.userMovieRepository.find({
      where: { userId, status: MovieStatus.WATCHED },
      relations: ['movie'],
    });
    const stats = await this.generateWatchedStats(watchedMovies);
    return stats.genresDistribution;
  }

  // placeholder per statistiche per anno
  async getYearStats(userId: string): Promise<YearStats[]> {
    // todo: implementa se necessario
    return [];
  }

  // ritorna solo statistiche registi
  async getDirectorStats(userId: string): Promise<DirectorStats[]> {
    const watchedMovies = await this.userMovieRepository.find({
      where: { userId, status: MovieStatus.WATCHED },
      relations: ['movie'],
    });
    const stats = await this.generateWatchedStats(watchedMovies);
    return stats.topDirectors;
  }

  // alias per analytics complete (retrocompatibilità)
  async getAdvancedAnalytics(userId: string): Promise<any> {
    return this.getCompleteAnalytics(userId);
  }
}