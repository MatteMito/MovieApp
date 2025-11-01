// file: src/modules/tmdb/tmdb.service.ts
// tmdb service con cache intelligente e sync popular movies

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

    this.logger.log('tmdb service inizializzato (cache = movies table)');
  }

  // ============================================
  // enrichment singolo film
  // ============================================
  async enrichMovie(movie: Movie): Promise<Movie> {
    try {
      //step 1: controlla cache
      const existingMovie = await this.databaseService.findMovieByTitleYear(
        movie.title, 
        movie.year
      );

      if (existingMovie && existingMovie.tmdb_id && existingMovie.is_enriched) {
        this.logger.log(`cache hit: ${movie.title}`);
        return {
          ...existingMovie,
          id: movie.id,
          source: movie.source,
        };
      }

      //step 2: cerca su tmdb
      this.logger.log(`ricerca tmdb: ${movie.title} (${movie.year})`);

      await this.enforceRateLimit();
      let tmdbMovie: TmdbMovieDetails | null = null;

      //strategia 1: imdb id
      if (movie.id.startsWith('tt')) {
        tmdbMovie = await this.findByImdbId(movie.id);
        if (tmdbMovie) {
          this.logger.log(`trovato via imdb id: ${tmdbMovie.id} per ${movie.title}`);
        }
      }

      //strategia 2: titolo e anno
      if (!tmdbMovie) {
        tmdbMovie = await this.searchByTitle(movie.title, movie.year);
        if (tmdbMovie) {
          this.logger.log(`trovato via titolo: ${tmdbMovie.id} per ${movie.title}`);
        }
      }

      if (!tmdbMovie) {
        this.logger.warn(`nessun risultato tmdb per: ${movie.title}`);
        return movie;
      }

      //step 3: crea film arricchito
      const enrichedMovie = this.mapTmdbToMovie(movie, tmdbMovie);
      
      //step 4: salva in database
      await this.databaseService.saveMovie(enrichedMovie);
      
      this.logger.log(`film arricchito e salvato: ${movie.title} (tmdb_id: ${tmdbMovie.id})`);

      return enrichedMovie;
    } catch (error) {
      this.logger.error(`errore enrichment per ${movie.title}: ${error.message}`);
      return movie;
    }
  }

  // ============================================
  // enrichment batch
  // ============================================
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

    this.logger.log(`avvio enrichment batch per ${movies.length} film`);

    const moviesToEnrich: Movie[] = [];
    
    //step 1: separa cache hits
    for (const movie of movies) {
      const existing = await this.databaseService.findMovieByTitleYear(
        movie.title,
        movie.year
      );
      
      if (existing && existing.tmdb_id && existing.is_enriched) {
        const merged = {
          ...existing,
          id: movie.id,
          source: movie.source,
        };
        results.successfulMovies.push(merged);
        this.logger.debug(`cache hit: ${movie.title}`);
      } else {
        moviesToEnrich.push(movie);
      }
    }

    this.logger.log(`film da arricchire: ${moviesToEnrich.length}`);
    this.logger.log(`cache hits: ${results.successfulMovies.length}`);

    //step 2: arricchisci rimanenti
    for (let i = 0; i < moviesToEnrich.length; i++) {
      const movie = moviesToEnrich[i];
      
      try {
        const enriched = await this.enrichMovie(movie);
        
        if (enriched.tmdb_id) {
          results.successfulMovies.push(enriched);
        } else {
          results.failedMovies.push({
            movie,
            error: 'tmdb id non trovato'
          });
        }

        results.totalProcessed++;

        //progress callback
        if (options?.onProgress) {
          const totalCache = results.successfulMovies.length - (moviesToEnrich.length - i - 1);
          await options.onProgress(
            results.totalProcessed + results.successfulMovies.length - moviesToEnrich.length + results.totalProcessed,
            movies.length,
            movie.title
          );
        }

        //log progress
        if (results.totalProcessed % 5 === 0) {
          this.logger.log(
            `progress: ${results.totalProcessed}/${moviesToEnrich.length} nuovi arricchiti (${results.successfulMovies.length}/${movies.length} totali)`
          );
        }

      } catch (error) {
        this.logger.error(`errore per ${movie.title}: ${error.message}`);
        results.failedMovies.push({
          movie,
          error: error.message
        });
        results.totalProcessed++;
      }

      //rate limiting
      if (moviesToEnrich.length > 50 && i % 5 === 0) {
        await new Promise(resolve => setTimeout(resolve, 100));
      }
    }

    results.successRate = movies.length > 0
      ? (results.successfulMovies.length / movies.length) * 100
      : 0;

    this.logger.log(`enrichment completato:`);
    this.logger.log(`  successi: ${results.successfulMovies.length}/${movies.length}`);
    this.logger.log(`  falliti: ${results.failedMovies.length}`);
    this.logger.log(`  tasso successo: ${results.successRate.toFixed(2)}%`);

    return results;
  }

  // ============================================
  // sync film popolari (placeholder per autocomplete)
  // ============================================
  async syncPopularMovies(limit: number = 10000): Promise<{ synced: number; errors: number }> {
    this.logger.log(`sync film popolari tmdb (limit: ${limit})`);

    let synced = 0;
    let errors = 0;
    const batchSize = 20;
    const totalPages = Math.ceil(limit / batchSize);

    for (let page = 1; page <= totalPages; page++) {
      try {
        await this.enforceRateLimit();

        const url = `${this.baseUrl}/movie/popular`;
        const response = await firstValueFrom(
          this.httpService.get<TmdbSearchResponse>(url, {
            params: {
              api_key: this.apiKey,
              language: 'it-IT',
              page: page,
            },
          }),
        );

        if (!response.data.results || response.data.results.length === 0) {
          this.logger.warn(`nessun risultato alla pagina ${page}`);
          break;
        }

        for (const tmdbMovie of response.data.results) {
          try {
            //controlla se esiste
            const existing = await this.databaseService.findMovieByTitleYear(
              tmdbMovie.title,
              tmdbMovie.release_date ? new Date(tmdbMovie.release_date).getFullYear() : undefined,
            );

            if (existing) {
              this.logger.debug(`skip: ${tmdbMovie.title} (gia presente)`);
              continue;
            }

            //crea placeholder (is_enriched = false)
            const movie: Movie = {
              id: `tmdb_${tmdbMovie.id}`,
              title: tmdbMovie.title,
              year: tmdbMovie.release_date
                ? new Date(tmdbMovie.release_date).getFullYear()
                : undefined,
              source: 'TMDB',
              
              //solo dati base
              tmdb_id: tmdbMovie.id,
              overview: tmdbMovie.overview,
              poster_url: tmdbMovie.poster_path
                ? `https://image.tmdb.org/t/p/w500${tmdbMovie.poster_path}`
                : undefined,
              backdrop_url: tmdbMovie.backdrop_path
                ? `https://image.tmdb.org/t/p/original${tmdbMovie.backdrop_path}`
                : undefined,
              popularity: tmdbMovie.popularity,
              tmdb_rating: tmdbMovie.vote_average,
              vote_count: tmdbMovie.vote_count,
              
              //lascia vuoto (non arricchito)
              genres: [],
              actors: [],
              is_enriched: false,
            };

            await this.databaseService.saveMovie(movie);
            synced++;

            if (synced % 100 === 0) {
              this.logger.log(`${synced} film sincronizzati`);
            }

          } catch (error) {
            this.logger.error(`errore ${tmdbMovie.title}: ${error.message}`);
            errors++;
          }
        }

        //pausa tra pagine
        await new Promise(resolve => setTimeout(resolve, 300));

      } catch (error) {
        this.logger.error(`errore pagina ${page}: ${error.message}`);
        errors++;
      }
    }

    this.logger.log(`sync completato: ${synced} film, ${errors} errori`);

    return { synced, errors };
  }

  // ============================================
  // metodi pubblici per controller
  // ============================================

  /**
   * cerca film su tmdb per titolo
   */
  async searchByTitle(
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
            year: year,
            language: 'it-IT',
            include_adult: false,
          },
        }),
      );

      if (!response.data.results || response.data.results.length === 0) {
        return null;
      }

      const bestMatch = response.data.results[0];
      return await this.getMovieDetails(bestMatch.id);
    } catch (error) {
      this.logger.error(`errore ricerca titolo ${title}: ${error.message}`);
      return null;
    }
  }

  /**
   * ottieni dettagli film da tmdb id
   */
  async getMovieDetails(tmdbId: number): Promise<TmdbMovieDetails | null> {
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
        }),
      );

      return response.data;
    } catch (error) {
      this.logger.error(`errore dettagli film ${tmdbId}: ${error.message}`);
      return null;
    }
  }

  /**
   * autocomplete veloce da database locale
   */
  async searchForAutocomplete(query: string, limit: number = 10): Promise<Movie[]> {
    try {
      return await this.databaseService.searchMoviesForAutocomplete(query, limit);
    } catch (error) {
      this.logger.error(`errore autocomplete: ${error.message}`);
      return [];
    }
  }

  // ============================================
  // metodi privati
  // ============================================

  private async enforceRateLimit(): Promise<void> {
    const now = Date.now();
    this.requestHistory = this.requestHistory.filter(
      (timestamp) => now - timestamp < this.rateLimitWindow
    );

    if (this.requestHistory.length >= this.maxRequestsPerWindow) {
      const oldestRequest = this.requestHistory[0];
      const waitTime = this.rateLimitWindow - (now - oldestRequest) + 100;
      
      this.logger.debug(`rate limit raggiunto, attesa ${waitTime}ms`);
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

  private mapTmdbToMovie(originalMovie: Movie, tmdbData: TmdbMovieDetails): Movie {
    const director = tmdbData.credits?.crew
      ?.find((c) => c.job === 'Director')?.name;

    const actors = tmdbData.credits?.cast
      ?.slice(0, 10)
      .map((c) => c.name) || [];

    const genres = tmdbData.genres?.map((g) => g.name) || [];

    const keywords = tmdbData.keywords?.keywords
      ?.slice(0, 10)
      .map((k) => k.name) || [];

    const productionCompanies = tmdbData.production_companies
      ?.map((c) => c.name) || [];

    const productionCountries = tmdbData.production_countries
      ?.map((c) => c.name) || [];

    const spokenLanguages = tmdbData.spoken_languages
      ?.map((l) => l.english_name) || [];

    const certification = tmdbData.releases?.countries
      ?.find((c) => c.iso_3166_1 === 'IT')?.certification;

    const trailer = tmdbData.videos?.results
      ?.find((v) => v.type === 'Trailer' && v.site === 'YouTube');

    return {
      id: originalMovie.id,
      title: tmdbData.title,
      year: tmdbData.release_date
        ? new Date(tmdbData.release_date).getFullYear()
        : originalMovie.year,
      source: originalMovie.source,
      tmdb_id: tmdbData.id,
      is_enriched: true,
      genres,
      director,
      actors,
      overview: tmdbData.overview,
      tagline: tmdbData.tagline,
      runtime: tmdbData.runtime,
      poster_url: tmdbData.poster_path
        ? `https://image.tmdb.org/t/p/w500${tmdbData.poster_path}`
        : undefined,
      backdrop_url: tmdbData.backdrop_path
        ? `https://image.tmdb.org/t/p/original${tmdbData.backdrop_path}`
        : undefined,
      tmdb_rating: tmdbData.vote_average,
      vote_count: tmdbData.vote_count,
      popularity: tmdbData.popularity,
      budget: tmdbData.budget,
      revenue: tmdbData.revenue,
      status: tmdbData.status,
      production_companies: productionCompanies,
      production_countries: productionCountries,
      original_language: tmdbData.original_language,
      original_title: tmdbData.original_title,
      spoken_languages: spokenLanguages,
      adult: tmdbData.adult,
      homepage: tmdbData.homepage,
      imdb_id: tmdbData.imdb_id,
      keywords,
      certification,
      trailer_url: trailer
        ? `https://www.youtube.com/watch?v=${trailer.key}`
        : undefined,
    };
  }
}