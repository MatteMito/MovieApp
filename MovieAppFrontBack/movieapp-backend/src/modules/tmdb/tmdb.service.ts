// integrazione tmdb api con cache intelligente e flag is_enriched

import { Injectable, Logger } from '@nestjs/common';
import { HttpService } from '@nestjs/axios';
import { ConfigService } from '@nestjs/config';
import { firstValueFrom } from 'rxjs';
import { DatabaseService } from '../../database/database.service';
import {
  Movie,
  TmdbSearchResponse,
  TmdbFindResponse,
  TmdbMovieDetails,
  TmdbMovie,
} from '../../common/interfaces/movie.interface';

@Injectable()
export class TmdbService {
  private readonly logger = new Logger(TmdbService.name);
  private readonly baseUrl: string;
  private readonly apiKey: string;

  // rate limiting per api tmdb (35 richieste per 10 secondi)
  private readonly rateLimitWindow = 10000; // 10 secondi
  private readonly maxRequestsPerWindow = 35;
  private requestHistory: number[] = [];

  constructor(
    private readonly httpService: HttpService,
    private readonly configService: ConfigService,
    private readonly databaseService: DatabaseService,
  ) {
    this.baseUrl = this.configService.get<string>('TMDB_BASE_URL') || 
      'https://api.themoviedb.org/3';
    this.apiKey = this.configService.get<string>('TMDB_API_KEY');

    if (!this.apiKey) {
      throw new Error('TMDB_API_KEY non trovata nelle variabili ambiente');
    }

    this.logger.log('✅ tmdb service inizializzato con cache e rate limiting');
  }

  // ENRICHMENT FILM CON CACHE INTELLIGENTE

  /**
   * arricchisce singolo film con dati tmdb, controllando prima il database per flag is_enriched
   * e utilizza cache database per ridurre chiamate api
   */
  async enrichMovie(movie: Movie): Promise<Movie> {
    const cacheKey = this.generateTmdbCacheKey(movie);

    try {
      // STEP 1: controlla se film già arricchito in database
      const existingMovie = await this.databaseService.findMovieByTitleYear(
        movie.title, 
        movie.year
      );

      if (existingMovie && existingMovie.tmdb_id) {
        this.logger.log(`✅ film già arricchito in db: ${movie.title} (skip tmdb api)`);
        // merge dati utente con dati arricchiti esistenti
        return {
          ...existingMovie,
          id: movie.id,
          user_rating: movie.user_rating,
          watched_date: movie.watched_date,
          user_review: movie.user_review,
          is_watched: movie.is_watched,
          source: movie.source,
        };
      }

      // STEP 2: controlla cache tmdb
      const cachedTmdbData = await this.databaseService.getTmdbCache(cacheKey);
      if (cachedTmdbData) {
        this.logger.log(`✅ cache tmdb hit: ${movie.title} (${movie.year})`);
        return this.mapTmdbToMovie(movie, cachedTmdbData);
      }

      this.logger.log(`🔍 ricerca tmdb: ${movie.title} (${movie.year})`);

      await this.enforceRateLimit();
      let tmdbMovie: TmdbMovieDetails | null = null;

      // strategia 1: ricerca per imdb id se disponibile
      if (movie.id.startsWith('tt')) {
        tmdbMovie = await this.findByImdbIdWithCache(movie.id, cacheKey);
        if (tmdbMovie) {
          this.logger.log(`✅ trovato via imdb id: ${tmdbMovie.id} per ${movie.title}`);
        }
      }

      // strategia 2: fallback ricerca per titolo e anno
      if (!tmdbMovie) {
        tmdbMovie = await this.searchByTitleWithCache(movie.title, movie.year, cacheKey);
        if (tmdbMovie) {
          this.logger.log(`✅ trovato via titolo: ${tmdbMovie.id} per ${movie.title}`);
        }
      }

      // se non trovato, ritorna film originale senza enrichment
      if (!tmdbMovie) {
        this.logger.warn(`⚠️ nessun risultato tmdb per: ${movie.title}`);
        return movie;
      }

      // estrai dati importanti
      const director = tmdbMovie.credits?.crew?.find(
        (person: any) => person.job === 'Director',
      )?.name || 'unknown';

      const cast = tmdbMovie.credits?.cast
        ?.slice(0, 5)
        .map((actor: any) => actor.name) || [];

      const genres = tmdbMovie.genres?.map((g: any) => g.name) || [];

      const keywords = tmdbMovie.keywords?.keywords
        ?.slice(0, 10)
        .map((k: any) => k.name) || [];

      this.logger.log(`📊 dati estratti per ${movie.title}:`);
      this.logger.log(`   regista: ${director}`);
      this.logger.log(`   generi: ${genres.join(', ')}`);
      this.logger.log(`   cast: ${cast.join(', ')}`);

      // salva in cache per uso futuro
      await this.databaseService.saveTmdbCache(cacheKey, tmdbMovie);

      const enrichedMovie = this.mapTmdbToMovie(movie, tmdbMovie);
      this.logger.log(`✅ film arricchito e cachato: ${movie.title} (tmdb_id: ${tmdbMovie.id})`);

      return enrichedMovie;
    } catch (error) {
      this.logger.error(`❌ errore enrichment per ${movie.title}: ${error.message}`);
      return movie;
    }
  }

  /**
   * enrichment batch ottimizzato con rate limiting saltando film già arricchiti in database
   * processa film in batch per rispettare limiti API
   */
  async enrichMovies(
    movies: Movie[],
    options?: {
      onProgress?: (processed: number, total: number, currentMovie?: string) => Promise<void>;
    },
  ): Promise<{
    successfulMovies: Movie[];
    failedMovies: Array<{ movie: Movie; error: string }>;
    totalProcessed: number;
    successRate: number;
  }> {
    const results = {
      successfulMovies: [] as Movie[],
      failedMovies: [] as Array<{ movie: Movie; error: string }>,
      totalProcessed: 0,
      successRate: 0,
    };

    this.logger.log(`🎬 avvio enrichment batch per ${movies.length} film`);

    // STEP 1: separa film già arricchiti da quelli da processare
    const moviesToEnrich: Movie[] = [];
    
    for (const movie of movies) {
      const existing = await this.databaseService.findMovieByTitleYear(
        movie.title,
        movie.year
      );
      
      if (existing && existing.tmdb_id) {
        // film già arricchito, usa dati esistenti
        const merged = {
          ...existing,
          id: movie.id,
          user_rating: movie.user_rating,
          watched_date: movie.watched_date,
          user_review: movie.user_review,
          is_watched: movie.is_watched,
          source: movie.source,
        };
        results.successfulMovies.push(merged);
        this.logger.debug(`skip enrichment (già fatto): ${movie.title}`);
      } else {
        moviesToEnrich.push(movie);
      }
    }

    this.logger.log(`📊 film da arricchire: ${moviesToEnrich.length}/${movies.length}`);
    this.logger.log(`✅ film già arricchiti: ${results.successfulMovies.length}`);

    // STEP 2: processa solo film non arricchiti
    // processing in batch per rispettare rate limits
    const batchSize = 5;
    for (let i = 0; i < moviesToEnrich.length; i += batchSize) {
      const batch = moviesToEnrich.slice(i, i + batchSize);

      const promises = batch.map(async (movie, batchIndex) => {
        try {
          const enriched = await this.enrichMovie(movie);

          const totalProcessed = results.successfulMovies.length + i + batchIndex + 1;
          if (options?.onProgress) {
            await options.onProgress(totalProcessed, movies.length, movie.title);
          }

          return { success: true, movie: enriched };
        } catch (error) {
          return {
            success: false,
            movie,
            error: error.message,
          };
        }
      });

      const batchResults = await Promise.all(promises);

      batchResults.forEach((result) => {
        results.totalProcessed++;
        if (result.success) {
          results.successfulMovies.push(result.movie);
        } else {
          results.failedMovies.push({
            movie: result.movie,
            error: result.error,
          });
        }
      });

      // pausa tra batch per rispettare rate limiting
      if (i + batchSize < moviesToEnrich.length) {
        await this.delay(2000);
        this.logger.debug(`batch ${Math.floor(i / batchSize) + 1} completato, pausa rate limiting`);
      }
    }

    results.successRate = results.successfulMovies.length / movies.length;

    const enrichedCount = results.successfulMovies.filter((m) => m.tmdb_id).length;
    this.logger.log(
      `✅ enrichment batch completato: ${enrichedCount}/${movies.length} film arricchiti`,
    );

    return results;
  }

  // RICERCA TMDB

  /**
   * ricerca per imdb id con cache automatica
   */
  private async findByImdbIdWithCache(
    imdbId: string,
    cacheKey: string,
  ): Promise<TmdbMovieDetails | null> {
    try {
      const cleanImdbId = imdbId.startsWith('tt') ? imdbId : `tt${imdbId}`;

      await this.enforceRateLimit();
      const url = `${this.baseUrl}/find/${cleanImdbId}`;

      const response = await firstValueFrom(
        this.httpService.get<TmdbFindResponse>(url, {
          params: {
            api_key: this.apiKey,
            external_source: 'imdb_id',
            language: 'it-IT',
          },
          timeout: 15000,
        }),
      );

      this.recordApiCall();

      if (response.data.movie_results?.length > 0) {
        const movieId = response.data.movie_results[0].id;
        const movieDetails = await this.getMovieDetailsWithCache(movieId, cacheKey);
        return movieDetails;
      }

      return null;
    } catch (error) {
      this.logger.error(`errore ricerca imdb id ${imdbId}: ${error.message}`);
      return null;
    }
  }

  /**
   * ricerca per titolo con fuzzy matching
   */
  private async searchByTitleWithCache(
    title: string,
    year?: number,
    cacheKey?: string,
  ): Promise<TmdbMovieDetails | null> {
    try {
      await this.enforceRateLimit();
      const url = `${this.baseUrl}/search/movie`;

      const response = await firstValueFrom(
        this.httpService.get<TmdbSearchResponse>(url, {
          params: {
            api_key: this.apiKey,
            query: title,
            year: year || undefined,
            language: 'it-IT',
            include_adult: false,
          },
          timeout: 15000,
        }),
      );

      this.recordApiCall();

      if (response.data.results?.length > 0) {
        const bestMatch = this.findBestMatch(response.data.results, title, year);
        if (bestMatch) {
          const movieDetails = await this.getMovieDetailsWithCache(bestMatch.id, cacheKey);
          return movieDetails;
        }
      }

      return null;
    } catch (error) {
      this.logger.error(`errore ricerca titolo ${title}: ${error.message}`);
      return null;
    }
  }

  /**
   * recupera dettagli completi film con cache
   */
  private async getMovieDetailsWithCache(
    tmdbId: number,
    cacheKey?: string,
  ): Promise<TmdbMovieDetails | null> {
    try {
      await this.enforceRateLimit();
      const url = `${this.baseUrl}/movie/${tmdbId}`;

      const response = await firstValueFrom(
        this.httpService.get<TmdbMovieDetails>(url, {
          params: {
            api_key: this.apiKey,
            language: 'it-IT',
            append_to_response: 'credits,keywords,videos,releases',
          },
          timeout: 20000,
        }),
      );

      this.recordApiCall();
      const movieDetails = response.data;

      if (cacheKey) {
        await this.databaseService.saveTmdbCache(cacheKey, movieDetails);
      }

      return movieDetails;
    } catch (error) {
      this.logger.error(`errore dettagli film id ${tmdbId}: ${error.message}`);
      return null;
    }
  }

  // RATE LIMITING

  /**
   * enforza rate limiting per api tmdb
   * previene superamento limiti (35 req/10sec)
   */
  private async enforceRateLimit(): Promise<void> {
    const now = Date.now();

    // rimuovi richieste fuori dalla finestra temporale
    this.requestHistory = this.requestHistory.filter(
      (timestamp) => now - timestamp < this.rateLimitWindow,
    );

    // se limite raggiunto, attendi
    if (this.requestHistory.length >= this.maxRequestsPerWindow) {
      const oldestRequest = Math.min(...this.requestHistory);
      const waitTime = this.rateLimitWindow - (now - oldestRequest) + 100;

      this.logger.debug(`⏳ rate limit raggiunto, attesa ${waitTime}ms`);
      await this.delay(waitTime);
    }
  }

  /**
   * registra chiamata api per tracking rate limit
   */
  private recordApiCall(): void {
    this.requestHistory.push(Date.now());
  }

  // UTILITY METHODS

  /**
   * genera chiave cache normalizzata per film
   */
  private generateTmdbCacheKey(movie: Movie): string {
    const titleKey = movie.title
      .toLowerCase()
      .replace(/[^a-z0-9\s]/g, '')
      .replace(/\s+/g, '_')
      .substring(0, 50);

    return `${titleKey}_${movie.year || 'unknown'}`;
  }

  /**
   * trova miglior match tra risultati ricerca
   * usa scoring basato su titolo, anno e popolarità
   */
  private findBestMatch(
    movies: TmdbMovie[],
    originalTitle: string,
    originalYear?: number,
  ): TmdbMovie | null {
    if (!movies || movies.length === 0) return null;

    return movies.reduce((best, current) => {
      let currentScore = 0;
      let bestScore = 0;

      const currentTitleLower = current.title.toLowerCase();
      const originalTitleLower = originalTitle.toLowerCase();

      // scoring titolo
      if (currentTitleLower === originalTitleLower) {
        currentScore += 20;
      } else if (
        currentTitleLower.includes(originalTitleLower) ||
        originalTitleLower.includes(currentTitleLower)
      ) {
        currentScore += 10;
      }

      if (best) {
        const bestTitleLower = best.title.toLowerCase();
        if (bestTitleLower === originalTitleLower) {
          bestScore += 20;
        } else if (
          bestTitleLower.includes(originalTitleLower) ||
          originalTitleLower.includes(bestTitleLower)
        ) {
          bestScore += 10;
        }
      }

      // scoring anno
      if (originalYear && current.release_date) {
        const currentYear = new Date(current.release_date).getFullYear();
        if (currentYear === originalYear) {
          currentScore += 15;
        } else {
          const yearDiff = Math.abs(currentYear - originalYear);
          currentScore -= yearDiff;
        }
      }

      if (originalYear && best && best.release_date) {
        const bestYear = new Date(best.release_date).getFullYear();
        if (bestYear === originalYear) {
          bestScore += 15;
        } else {
          const yearDiff = Math.abs(bestYear - originalYear);
          bestScore -= yearDiff;
        }
      }

      // tiebreaker: popolarità
      if (Math.abs(currentScore - bestScore) <= 2) {
        currentScore += (current.popularity || 0) * 0.1;
        if (best) bestScore += (best.popularity || 0) * 0.1;
      }

      return currentScore > bestScore ? current : best;
    });
  }

  /**
   * mappa dati tmdb in movie model
   */
  private mapTmdbToMovie(original: Movie, tmdb: TmdbMovieDetails): Movie {
    return {
      ...original,
      tmdb_id: tmdb.id,
      genres: tmdb.genres?.map((g) => g.name) || [],
      director: tmdb.credits?.crew?.find((c) => c.job === 'Director')?.name,
      cast: tmdb.credits?.cast?.slice(0, 10).map((c) => c.name) || [],
      overview: tmdb.overview,
      tagline: tmdb.tagline,
      poster_url: tmdb.poster_path
        ? `https://image.tmdb.org/t/p/w500${tmdb.poster_path}`
        : undefined,
      backdrop_url: tmdb.backdrop_path
        ? `https://image.tmdb.org/t/p/w1280${tmdb.backdrop_path}`
        : undefined,
      tmdb_rating: tmdb.vote_average,
      vote_count: tmdb.vote_count,
      popularity: tmdb.popularity,
      runtime: tmdb.runtime,
      budget: tmdb.budget,
      revenue: tmdb.revenue,
      status: tmdb.status,
      original_language: tmdb.original_language,
      original_title: tmdb.original_title,
      adult: tmdb.adult,
      homepage: tmdb.homepage,
      imdb_id: tmdb.imdb_id,
      production_companies: tmdb.production_companies?.map((c) => c.name) || [],
      production_countries: tmdb.production_countries?.map((c) => c.name) || [],
      spoken_languages: tmdb.spoken_languages?.map((l) => l.name) || [],
      keywords: tmdb.keywords?.keywords?.map((k) => k.name) || [],
      certification: tmdb.releases?.countries?.find((c) => c.iso_3166_1 === 'IT')?.certification,
      trailer_url: tmdb.videos?.results?.find((v) => v.type === 'Trailer' && v.site === 'YouTube')
        ?.key
        ? `https://www.youtube.com/watch?v=${tmdb.videos.results.find((v) => v.type === 'Trailer' && v.site === 'YouTube')?.key}`
        : undefined,
    };
  }

  /**
   * utility: delay per rate limiting
   */
  private delay(ms: number): Promise<void> {
    return new Promise((resolve) => setTimeout(resolve, ms));
  }

  // HEALTH CHECK & STATS

  /**
   * health check api tmdb
   */
  async healthCheck(): Promise<{ status: string; details?: any }> {
    try {
      const testUrl = `${this.baseUrl}/configuration`;

      await this.enforceRateLimit();
      const response = await firstValueFrom(
        this.httpService.get(testUrl, {
          params: { api_key: this.apiKey },
          timeout: 10000,
        }),
      );
      this.recordApiCall();

      const cacheStats = await this.databaseService.getTmdbCacheStats();

      return {
        status: 'healthy',
        details: {
          tmdbApi: 'connected',
          baseUrl: this.baseUrl,
          cacheIntegration: 'active',
          enrichmentOptimization: 'enabled with is_enriched flag',
          rateLimiting: `${this.requestHistory.length}/${this.maxRequestsPerWindow} requests`,
          cacheStats: cacheStats,
          timestamp: new Date().toISOString(),
        },
      };
    } catch (error) {
      return {
        status: 'unhealthy',
        details: {
          error: error.message,
          cacheIntegration: this.databaseService.isDatabaseAvailable() ? 'active' : 'disabled',
          timestamp: new Date().toISOString(),
        },
      };
    }
  }

  /**
   * statistiche utilizzo api
   */
  getApiUsageStats(): any {
    const now = Date.now();
    const recentCalls = this.requestHistory.filter(
      (timestamp) => now - timestamp < this.rateLimitWindow,
    );

    return {
      recentCalls: recentCalls.length,
      maxAllowed: this.maxRequestsPerWindow,
      rateLimitWindow: `${this.rateLimitWindow / 1000}s`,
      utilizationPercentage: Math.round((recentCalls.length / this.maxRequestsPerWindow) * 100),
      canMakeRequest: recentCalls.length < this.maxRequestsPerWindow,
      nextResetIn:
        recentCalls.length > 0
          ? Math.max(0, this.rateLimitWindow - (now - Math.min(...recentCalls)))
          : 0,
    };
  }
}