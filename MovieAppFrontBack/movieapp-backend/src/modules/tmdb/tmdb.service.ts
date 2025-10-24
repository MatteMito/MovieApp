// File: src/modules/tmdb/tmdb.service.ts
// AGGIORNATO: rimossi riferimenti a campi utente

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

  private readonly rateLimitWindow = 10000;
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

    this.logger.log('✅ tmdb service inizializzato');
  }

  /**
   * Arricchisce singolo film con dati TMDB
   * IMPORTANTE: Non gestisce più campi utente (user_rating, is_watched, etc.)
   */
  async enrichMovie(movie: Movie): Promise<Movie> {
    const cacheKey = this.generateTmdbCacheKey(movie);

    try {
      // STEP 1: Controlla se film già arricchito in database
      const existingMovie = await this.databaseService.findMovieByTitleYear(
        movie.title, 
        movie.year
      );

      if (existingMovie && existingMovie.tmdb_id) {
        this.logger.log(`✅ film già arricchito in db: ${movie.title} (skip tmdb api)`);
        // ⚠️ RIMOSSO: merge con dati utente (ora gestiti separatamente)
        return {
          ...existingMovie,
          id: movie.id, // Mantieni l'ID originale
          source: movie.source, // Mantieni la source originale
        };
      }

      // STEP 2: Controlla cache TMDB
      const cachedTmdbData = await this.databaseService.getTmdbCache(cacheKey);
      if (cachedTmdbData) {
        this.logger.log(`✅ cache tmdb hit: ${movie.title} (${movie.year})`);
        return this.mapTmdbToMovie(movie, cachedTmdbData);
      }

      this.logger.log(`🔍 ricerca tmdb: ${movie.title} (${movie.year})`);

      await this.enforceRateLimit();
      let tmdbMovie: TmdbMovieDetails | null = null;

      // Strategia 1: Ricerca per IMDB ID se disponibile
      if (movie.id.startsWith('tt')) {
        tmdbMovie = await this.findByImdbIdWithCache(movie.id, cacheKey);
        if (tmdbMovie) {
          this.logger.log(`✅ trovato via imdb id: ${tmdbMovie.id} per ${movie.title}`);
        }
      }

      // Strategia 2: Fallback ricerca per titolo e anno
      if (!tmdbMovie) {
        tmdbMovie = await this.searchByTitleWithCache(movie.title, movie.year, cacheKey);
        if (tmdbMovie) {
          this.logger.log(`✅ trovato via titolo: ${tmdbMovie.id} per ${movie.title}`);
        }
      }

      if (!tmdbMovie) {
        this.logger.warn(`⚠️ nessun risultato tmdb per: ${movie.title}`);
        return movie;
      }

      // Salva in cache
      await this.databaseService.saveTmdbCache(cacheKey, tmdbMovie);

      const enrichedMovie = this.mapTmdbToMovie(movie, tmdbMovie);
      this.logger.log(`✅ film arricchito: ${movie.title} (tmdb_id: ${tmdbMovie.id})`);

      return enrichedMovie;
    } catch (error) {
      this.logger.error(`❌ errore enrichment per ${movie.title}: ${error.message}`);
      return movie;
    }
  }

  /**
   * Enrichment batch con gestione cache intelligente
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

    const moviesToEnrich: Movie[] = [];
    
    // STEP 1: Separa film già arricchiti da quelli da processare
    for (const movie of movies) {
      const existing = await this.databaseService.findMovieByTitleYear(
        movie.title,
        movie.year
      );
      
      if (existing && existing.tmdb_id) {
        // Film già arricchito
        // ⚠️ RIMOSSO: merge con dati utente
        const merged = {
          ...existing,
          id: movie.id,
          source: movie.source,
        };
        results.successfulMovies.push(merged);
        this.logger.debug(`💰 cache hit database: ${movie.title}`);
      } else {
        moviesToEnrich.push(movie);
      }
    }

    this.logger.log(`📊 film da arricchire: ${moviesToEnrich.length}`);
    this.logger.log(`💰 cache hits: ${results.successfulMovies.length}`);

    // STEP 2: Arricchisci film rimanenti
    for (let i = 0; i < moviesToEnrich.length; i++) {
      const movie = moviesToEnrich[i];
      
      try {
        const enriched = await this.enrichMovie(movie);
        
        if (enriched.tmdb_id) {
          results.successfulMovies.push(enriched);
        } else {
          results.failedMovies.push({
            movie,
            error: 'TMDB data not found',
          });
        }

        if (options?.onProgress) {
          await options.onProgress(
            results.successfulMovies.length + results.failedMovies.length,
            movies.length,
            movie.title
          );
        }
      } catch (error) {
        this.logger.error(`errore arricchimento ${movie.title}: ${error.message}`);
        results.failedMovies.push({
          movie,
          error: error.message,
        });
      }
    }

    results.totalProcessed = results.successfulMovies.length + results.failedMovies.length;
    results.successRate = results.totalProcessed > 0
      ? results.successfulMovies.length / results.totalProcessed
      : 0;

    this.logger.log(`✅ enrichment batch completato:`);
    this.logger.log(`   successi: ${results.successfulMovies.length}`);
    this.logger.log(`   falliti: ${results.failedMovies.length}`);
    this.logger.log(`   success rate: ${(results.successRate * 100).toFixed(1)}%`);

    return results;
  }

  // ===== METODI PRIVATI =====

  private generateTmdbCacheKey(movie: Movie): string {
    return `${movie.title.toLowerCase()}_${movie.year || 'unknown'}`;
  }

  private async enforceRateLimit(): Promise<void> {
    const now = Date.now();
    this.requestHistory = this.requestHistory.filter(
      (timestamp) => now - timestamp < this.rateLimitWindow
    );

    if (this.requestHistory.length >= this.maxRequestsPerWindow) {
      const oldestRequest = this.requestHistory[0];
      const waitTime = this.rateLimitWindow - (now - oldestRequest) + 100;
      
      this.logger.debug(`⏳ rate limit raggiunto, attesa ${waitTime}ms`);
      await new Promise((resolve) => setTimeout(resolve, waitTime));
      
      return this.enforceRateLimit();
    }

    this.requestHistory.push(now);
  }

  private async findByImdbIdWithCache(
    imdbId: string,
    cacheKey: string,
  ): Promise<TmdbMovieDetails | null> {
    try {
      const url = `${this.baseUrl}/find/${imdbId}`;
      const response = await firstValueFrom(
        this.httpService.get<TmdbFindResponse>(url, {
          params: {
            api_key: this.apiKey,
            external_source: 'imdb_id',
            append_to_response: 'credits,keywords,videos,releases',
          },
        }),
      );

      const movie = response.data.movie_results[0];
      if (!movie) return null;

      return await this.getMovieDetails(movie.id);
    } catch (error) {
      this.logger.error(`errore ricerca imdb ${imdbId}: ${error.message}`);
      return null;
    }
  }

  private async searchByTitleWithCache(
    title: string,
    year: number | undefined,
    cacheKey: string,
  ): Promise<TmdbMovieDetails | null> {
    try {
      const url = `${this.baseUrl}/search/movie`;
      const response = await firstValueFrom(
        this.httpService.get<TmdbSearchResponse>(url, {
          params: {
            api_key: this.apiKey,
            query: title,
            year,
            include_adult: false,
          },
        }),
      );

      const bestMatch = this.findBestMatch(response.data.results, title, year);
      if (!bestMatch) return null;

      return await this.getMovieDetails(bestMatch.id);
    } catch (error) {
      this.logger.error(`errore ricerca titolo ${title}: ${error.message}`);
      return null;
    }
  }

  private async getMovieDetails(tmdbId: number): Promise<TmdbMovieDetails | null> {
    try {
      const url = `${this.baseUrl}/movie/${tmdbId}`;
      const response = await firstValueFrom(
        this.httpService.get<TmdbMovieDetails>(url, {
          params: {
            api_key: this.apiKey,
            append_to_response: 'credits,keywords,videos,releases',
          },
        }),
      );

      return response.data;
    } catch (error) {
      this.logger.error(`errore dettagli film ${tmdbId}: ${error.message}`);
      return null;
    }
  }

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

      // Scoring titolo
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

      // Scoring anno
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

      // Tiebreaker: popolarità
      if (Math.abs(currentScore - bestScore) <= 2) {
        currentScore += (current.popularity || 0) * 0.1;
        if (best) bestScore += (best.popularity || 0) * 0.1;
      }

      return currentScore > bestScore ? current : best;
    });
  }

  /**
   * Mappa dati TMDB in Movie model
   * ⚠️ NON include più campi utente
   */
  private mapTmdbToMovie(original: Movie, tmdb: TmdbMovieDetails): Movie {
    return {
      ...original,
      tmdb_id: tmdb.id,
      genres: tmdb.genres?.map((g) => g.name) || [],
      director: tmdb.credits?.crew?.find((c) => c.job === 'Director')?.name,
      actors: tmdb.credits?.cast?.slice(0, 10).map((c) => c.name) || [],
      overview: tmdb.overview,
      tagline: tmdb.tagline,
      poster_url: tmdb.poster_path
        ? `https://image.tmdb.org/t/p/w500${tmdb.poster_path}`
        : undefined,
      backdrop_url: tmdb.backdrop_path
        ? `https://image.tmdb.org/t/p/original${tmdb.backdrop_path}`
        : undefined,
      tmdb_rating: tmdb.vote_average,
      vote_count: tmdb.vote_count,
      runtime: tmdb.runtime,
      budget: tmdb.budget,
      revenue: tmdb.revenue,
      status: tmdb.status,
      original_language: tmdb.original_language,
      original_title: tmdb.original_title,
      popularity: tmdb.popularity,
      adult: tmdb.adult,
      homepage: tmdb.homepage,
      imdb_id: tmdb.imdb_id,
      production_companies: tmdb.production_companies?.map((c) => c.name) || [],
      production_countries: tmdb.production_countries?.map((c) => c.name) || [],
      spoken_languages: tmdb.spoken_languages?.map((l) => l.name) || [],
      keywords: tmdb.keywords?.keywords?.slice(0, 10).map((k) => k.name) || [],
      certification: tmdb.releases?.countries?.find((c) => c.iso_3166_1 === 'US')?.certification,
      trailer_url: tmdb.videos?.results?.find((v) => v.type === 'Trailer')?.key
        ? `https://www.youtube.com/watch?v=${tmdb.videos.results.find((v) => v.type === 'Trailer')?.key}`
        : undefined,
    };
  }
}