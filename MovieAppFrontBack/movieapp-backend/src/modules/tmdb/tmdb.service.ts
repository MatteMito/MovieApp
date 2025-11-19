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

  // rate limiting: max 35 richieste per finestra di 10 secondi
  private readonly rateLimitWindow = 10000;
  private readonly maxRequestsPerWindow = 35;
  private requestHistory: number[] = [];

  constructor(
    private readonly httpService: HttpService,
    private readonly configService: ConfigService,
    private readonly databaseService: DatabaseService,
  ) {
    // leggi configurazione tmdb da .env
    this.baseUrl = this.configService.get<string>('TMDB_BASE_URL') || 
      'https://api.themoviedb.org/3';
    this.apiKey = this.configService.get<string>('TMDB_API_KEY');

    if (!this.apiKey) {
      throw new Error('TMDB_API_KEY non trovata nelle variabili ambiente');
    }

    this.logger.log('tmdb service inizializzato (cache = movies table)');
  }

  // arricchisce un singolo film con dati tmdb
  async enrichMovie(movie: Movie): Promise<Movie> {
    try {
      this.logger.log(`ricerca tmdb: ${movie.title} (${movie.year})`);

      // cerca film su tmdb per titolo e anno
      const tmdbData = await this.searchByTitle(movie.title, movie.year);

      if (!tmdbData) {
        this.logger.warn(`film non trovato su tmdb: ${movie.title}`);
        return null;
      }

      this.logger.log(`trovato via titolo: ${tmdbData.id} per ${movie.title}`);

      //mantengo l'id originale del film (lbxd_xxx o imdb_xxx)
      const enrichedMovie: Movie = {
        id: movie.id,  // usa l'id originale, non tmdb_id
        title: tmdbData.title || movie.title,
        year: tmdbData.release_date 
          ? new Date(tmdbData.release_date).getFullYear() 
          : movie.year,
        source: movie.source,  // mantieni source originale (letterboxd, imdb, etc)
        
        // dati tmdb
        tmdb_id: tmdbData.id,
        is_enriched: true, // flag per cache
        genres: tmdbData.genres?.map(g => g.name) || [],
        director: tmdbData.credits?.crew
          ?.find(c => c.job === 'Director')?.name || movie.director,
        actors: tmdbData.credits?.cast
          ?.slice(0, 10)
          .map(a => a.name) || [],
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
        production_companies: tmdbData.production_companies?.map(c => c.name) || [],
        production_countries: tmdbData.production_countries?.map(c => c.name) || [],
        original_language: tmdbData.original_language,
        original_title: tmdbData.original_title,
        spoken_languages: tmdbData.spoken_languages?.map(l => l.english_name) || [],
        adult: tmdbData.adult,
        homepage: tmdbData.homepage,
        imdb_id: tmdbData.imdb_id,
        keywords: tmdbData.keywords?.keywords?.map(k => k.name) || [],
        certification: this.extractCertification(tmdbData),
        trailer_url: this.extractTrailerUrl(tmdbData.videos),
        
        // mantieni dati utente originali (rating, watched_date)
        user_rating: movie.user_rating,
        watched_date: movie.watched_date,
      };

      // salva nel database come cache
      await this.databaseService.saveMovie(enrichedMovie);

      this.logger.log(`film arricchito e salvato: ${enrichedMovie.title} (tmdb_id: ${enrichedMovie.tmdb_id})`);

      return enrichedMovie;

    } catch (error) {
      this.logger.error(`errore enrichment ${movie.title}: ${error.message}`);
      return null;
    }
  }

  // enrichment batch con progress callback
  async enrichMovies(
    movies: Movie[],
    options?: {
      onProgress?: (processed: number, total: number, currentMovie?: string) => void;
    },
  ): Promise<{
    successfulMovies: Movie[];
    failedMovies: Array<{ movie: Movie; error: string }>;
    successRate: number;
  }> {
    const results = {
      successfulMovies: [] as Movie[],
      failedMovies: [] as Array<{ movie: Movie; error: string }>,
      successRate: 0,
    };

    for (let i = 0; i < movies.length; i++) {
      const movie = movies[i];

      try {
        // arricchisci singolo film
        const enrichedMovie = await this.enrichMovie(movie);

        if (enrichedMovie.tmdb_id) {
          results.successfulMovies.push(enrichedMovie);
        } else {
          results.failedMovies.push({
            movie,
            error: 'no tmdb match found',
          });
        }

        // callback progress opzionale (per websocket)
        if (options?.onProgress) {
          options.onProgress(i + 1, movies.length, movie.title);
        }

        // pausa tra richieste per rate limiting
        await new Promise(resolve => setTimeout(resolve, 100));

      } catch (error) {
        results.failedMovies.push({
          movie,
          error: error.message,
        });
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

  // scarica top 10k film popolari da tmdb e salva come placeholder (is_enriched=false)
  async syncPopularMovies(limit: number = 10000): Promise<{ synced: number; errors: number }> {
    this.logger.log(`sync film popolari tmdb (limit: ${limit})`);

    let synced = 0;
    let errors = 0;
    const batchSize = 20; // film per pagina
    const totalPages = Math.ceil(limit / batchSize);

    for (let page = 1; page <= totalPages; page++) {
      try {
        await this.enforceRateLimit();

        // chiama api popular movies
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
            // controlla se film già presente nel db
            const existing = await this.databaseService.findMovieByTmdbId(tmdbMovie.id);

            if (existing) {
              this.logger.debug(`skip: ${tmdbMovie.title} (gia presente)`);
              continue;
            }

            // crea placeholder con solo dati base (is_enriched = false)
            const movie: Movie = {
              id: `tmdb_${tmdbMovie.id}`, // id composito per film popolari
              title: tmdbMovie.title,
              year: tmdbMovie.release_date
                ? new Date(tmdbMovie.release_date).getFullYear()
                : undefined,
              source: 'TMDB',
              
              // solo dati base per autocomplete
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
              
              // lascia vuoto (non completamente arricchito)
              genres: [],
              actors: [],
              is_enriched: false, // verrà arricchito quando utente lo aggiunge
            };

            await this.databaseService.saveMovie(movie);
            synced++;

            // log ogni 100 film
            if (synced % 100 === 0) {
              this.logger.log(`${synced} film sincronizzati`);
            }

          } catch (error) {
            this.logger.error(`errore ${tmdbMovie.title}: ${error.message}`);
            errors++;
          }
        }

        // pausa tra pagine
        await new Promise(resolve => setTimeout(resolve, 300));

      } catch (error) {
        this.logger.error(`errore pagina ${page}: ${error.message}`);
        errors++;
      }
    }

    this.logger.log(`sync completato: ${synced} film, ${errors} errori`);

    return { synced, errors };
  }

  // cerca film su tmdb per titolo e anno
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

      // prendi primo risultato (best match)
      const bestMatch = response.data.results[0];
      return await this.getMovieDetails(bestMatch.id);
    } catch (error) {
      this.logger.error(`errore ricerca titolo ${title}: ${error.message}`);
      return null;
    }
  }

  // cerca film su tmdb per imdb id
  async findByImdbId(imdbId: string): Promise<TmdbMovieDetails | null> {
    try {
      const url = `${this.baseUrl}/find/${imdbId}`;
      const response = await firstValueFrom(
        this.httpService.get<TmdbFindResponse>(url, {
          params: {
            api_key: this.apiKey,
            external_source: 'imdb_id',
            language: 'it-IT',
          },
        }),
      );

      if (!response.data.movie_results || response.data.movie_results.length === 0) {
        return null;
      }

      const movie = response.data.movie_results[0];
      return await this.getMovieDetails(movie.id);
    } catch (error) {
      this.logger.error(`errore ricerca imdb ${imdbId}: ${error.message}`);
      return null;
    }
  }

  // ottieni dettagli completi film da tmdb id
  async getMovieDetails(tmdbId: number): Promise<TmdbMovieDetails | null> {
    try {
      await this.enforceRateLimit();

      const url = `${this.baseUrl}/movie/${tmdbId}`;
      const response = await firstValueFrom(
        this.httpService.get<TmdbMovieDetails>(url, {
          params: {
            api_key: this.apiKey,
            language: 'it-IT',
            append_to_response: 'credits,keywords,videos,releases', // include cast, keywords, trailer
          },
        }),
      );

      return response.data;
    } catch (error) {
      this.logger.error(`errore dettagli film ${tmdbId}: ${error.message}`);
      return null;
    }
  }

  // autocomplete veloce da database locale (no api tmdb)
  async searchForAutocomplete(query: string, limit: number = 10): Promise<Movie[]> {
    try {
      // cerca nel db locale con ranking per popolarità
      return await this.databaseService.searchMoviesForAutocomplete(query, limit);
    } catch (error) {
      this.logger.error(`errore autocomplete: ${error.message}`);
      return [];
    }
  }

  // gestione rate limit tmdb api (max 35 req/10s)
  private async enforceRateLimit(): Promise<void> {
    const now = Date.now();
    // rimuovi richieste fuori finestra
    this.requestHistory = this.requestHistory.filter(
      (timestamp) => now - timestamp < this.rateLimitWindow
    );

    // se limite raggiunto, aspetta
    if (this.requestHistory.length >= this.maxRequestsPerWindow) {
      const oldestRequest = this.requestHistory[0];
      const waitTime = this.rateLimitWindow - (now - oldestRequest) + 100;
      
      this.logger.debug(`rate limit raggiunto, attesa ${waitTime}ms`);
      await new Promise((resolve) => setTimeout(resolve, waitTime));
    }

    // registra nuova richiesta
    this.requestHistory.push(Date.now());
  }

  // mappa dati tmdb completi a interfaccia movie
  private mapTmdbToMovie(originalMovie: Movie, tmdbMovie: TmdbMovieDetails): Movie {
    const director = tmdbMovie.credits?.crew
      .find((c) => c.job === 'Director')?.name;

    const actors = tmdbMovie.credits?.cast
      .slice(0, 10)
      .map((a) => a.name) || [];

    const keywords = tmdbMovie.keywords?.keywords
      .slice(0, 10)
      .map((k) => k.name) || [];

    const certification = this.extractCertification(tmdbMovie);
    const trailerUrl = this.extractTrailerUrl(tmdbMovie);

    return {
      id: originalMovie.id,
      title: tmdbMovie.title,
      year: tmdbMovie.release_date 
        ? new Date(tmdbMovie.release_date).getFullYear() 
        : undefined,
      source: originalMovie.source,

      // dati tmdb
      tmdb_id: tmdbMovie.id,
      is_enriched: true,

      // metadati
      genres: tmdbMovie.genres.map((g) => g.name),
      director: director,
      actors: actors,
      overview: tmdbMovie.overview,
      tagline: tmdbMovie.tagline,
      runtime: tmdbMovie.runtime,

      // poster e immagini
      poster_url: tmdbMovie.poster_path
        ? `https://image.tmdb.org/t/p/w500${tmdbMovie.poster_path}`
        : undefined,
      backdrop_url: tmdbMovie.backdrop_path
        ? `https://image.tmdb.org/t/p/original${tmdbMovie.backdrop_path}`
        : undefined,

      // rating e popolarità
      tmdb_rating: tmdbMovie.vote_average,
      vote_count: tmdbMovie.vote_count,
      popularity: tmdbMovie.popularity,

      // produzione
      budget: tmdbMovie.budget,
      revenue: tmdbMovie.revenue,
      status: tmdbMovie.status,
      production_companies: tmdbMovie.production_companies.map((c) => c.name),
      production_countries: tmdbMovie.production_countries.map((c) => c.name),

      // lingue
      original_language: tmdbMovie.original_language,
      original_title: tmdbMovie.original_title,
      spoken_languages: tmdbMovie.spoken_languages.map((l) => l.english_name),

      // metadata extra
      adult: tmdbMovie.adult,
      homepage: tmdbMovie.homepage,
      imdb_id: tmdbMovie.imdb_id,
      keywords: keywords,
      certification: certification,
      trailer_url: trailerUrl,
    };
  }

  // estrae certificazione film (rating censura: PG-13, R, etc)
  private extractCertification(tmdbMovie: any): string | undefined {
    try {
      const releases = tmdbMovie.releases?.countries || tmdbMovie.release_dates?.results || [];
      
      // cerca certificazione italia
      const italianRelease = releases.find((r: any) => r.iso_3166_1 === 'IT');
      if (italianRelease?.certification) {
        return italianRelease.certification;
      }

      // fallback: usa certificazione usa
      const usRelease = releases.find((r: any) => r.iso_3166_1 === 'US');
      if (usRelease?.certification) {
        return usRelease.certification;
      }

      return undefined;
    } catch (error) {
      return undefined;
    }
  }

  // estrae url trailer youtube
  private extractTrailerUrl(tmdbMovie: any): string | undefined {
    try {
      const videos = tmdbMovie.videos?.results || [];
      const trailer = videos.find(
        (v: any) => v.type === 'Trailer' && v.site === 'YouTube'
      );

      return trailer ? `https://www.youtube.com/watch?v=${trailer.key}` : undefined;
    } catch (error) {
      return undefined;
    }
  }
}