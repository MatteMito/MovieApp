// File: src/modules/tmdb/tmdb.service.ts
// ✅ OTTIMIZZATO: cache basata SOLO su tabella movies (no tmdb_cache)

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

    this.logger.log('✅ tmdb service inizializzato (cache = movies table)');
  }

  /**
   * ✅ Arricchisce singolo film
   * Cache intelligente: controlla prima la tabella movies (per tmdb_id)
   */
  async enrichMovie(movie: Movie): Promise<Movie> {
    try {
      // STEP 1: Controlla se film già arricchito in database movies
      const existingMovie = await this.databaseService.findMovieByTitleYear(
        movie.title, 
        movie.year
      );

      if (existingMovie && existingMovie.tmdb_id) {
        this.logger.log(`✅ CACHE HIT (movies table): ${movie.title}`);
        return {
          ...existingMovie,
          id: movie.id,
          source: movie.source,
        };
      }

      // STEP 2: Film non in cache, cerca su TMDB
      this.logger.log(`🔍 ricerca tmdb: ${movie.title} (${movie.year})`);

      await this.enforceRateLimit();
      let tmdbMovie: TmdbMovieDetails | null = null;

      // Strategia 1: Ricerca per IMDB ID se disponibile
      if (movie.id.startsWith('tt')) {
        tmdbMovie = await this.findByImdbId(movie.id);
        if (tmdbMovie) {
          this.logger.log(`✅ trovato via imdb id: ${tmdbMovie.id} per ${movie.title}`);
        }
      }

      // Strategia 2: Fallback ricerca per titolo e anno
      if (!tmdbMovie) {
        tmdbMovie = await this.searchByTitle(movie.title, movie.year);
        if (tmdbMovie) {
          this.logger.log(`✅ trovato via titolo: ${tmdbMovie.id} per ${movie.title}`);
        }
      }

      if (!tmdbMovie) {
        this.logger.warn(`⚠️ nessun risultato tmdb per: ${movie.title}`);
        return movie;
      }

      // STEP 3: Crea film arricchito
      const enrichedMovie = this.mapTmdbToMovie(movie, tmdbMovie);
      
      // STEP 4: Salva in database movies (così diventa cache per il futuro!)
      await this.databaseService.saveMovie(enrichedMovie);
      
      this.logger.log(`✅ film arricchito e salvato: ${movie.title} (tmdb_id: ${tmdbMovie.id})`);

      return enrichedMovie;
    } catch (error) {
      this.logger.error(`❌ errore enrichment per ${movie.title}: ${error.message}`);
      return movie;
    }
  }

  /**
   * ✅ Enrichment batch con cache intelligente
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
    
    // STEP 1: Separa film già arricchiti (cache hit) da quelli da processare
    for (const movie of movies) {
      const existing = await this.databaseService.findMovieByTitleYear(
        movie.title,
        movie.year
      );
      
      if (existing && existing.tmdb_id) {
        // Film già arricchito in cache (movies table)
        const merged = {
          ...existing,
          id: movie.id,
          source: movie.source,
        };
        results.successfulMovies.push(merged);
        this.logger.debug(`💰 CACHE HIT: ${movie.title}`);
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
          
          // ✅ FIX CRITICO: SALVA IL FILM ARRICCHITO NEL DATABASE!
          await this.databaseService.saveMovie(enriched);
          this.logger.debug(`✅ Film arricchito e salvato: ${enriched.title}`);
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

  private async findByImdbId(imdbId: string): Promise<TmdbMovieDetails | null> {
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

  private async searchByTitle(
    title: string,
    year: number | undefined,
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
    if (movies.length === 0) return null;

    const normalizeTitle = (title: string) =>
      title.toLowerCase().replace(/[^\w\s]/g, '').trim();

    const normalizedOriginal = normalizeTitle(originalTitle);

    let bestMatch = movies[0];
    let bestScore = 0;

    for (const movie of movies) {
      const normalizedTitle = normalizeTitle(movie.title);
      let score = 0;

      if (normalizedTitle === normalizedOriginal) {
        score += 100;
      } else if (normalizedTitle.includes(normalizedOriginal)) {
        score += 50;
      } else if (normalizedOriginal.includes(normalizedTitle)) {
        score += 40;
      }

      if (originalYear && movie.release_date) {
        const movieYear = parseInt(movie.release_date.substring(0, 4));
        if (movieYear === originalYear) {
          score += 50;
        } else if (Math.abs(movieYear - originalYear) <= 1) {
          score += 20;
        }
      }

      if (movie.popularity) {
        score += Math.min(movie.popularity / 10, 10);
      }

      if (score > bestScore) {
        bestScore = score;
        bestMatch = movie;
      }
    }

    return bestMatch;
  }

  private mapTmdbToMovie(movie: Movie, tmdb: TmdbMovieDetails): Movie {
    return {
      ...movie,
      tmdb_id: tmdb.id,
      title: tmdb.title || movie.title,
      year: tmdb.release_date ? parseInt(tmdb.release_date.substring(0, 4)) : movie.year,
      genres: tmdb.genres?.map((g) => g.name) || [],
      director: tmdb.credits?.crew?.find((c) => c.job === 'Director')?.name,
      actors: tmdb.credits?.cast?.slice(0, 5).map((a) => a.name) || [],
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