// service per integrazione api the movie database
// gestisce: ricerca film, enrichment dati, sync popolari, autocomplete

import { Injectable, Logger } from '@nestjs/common';
import { HttpService } from '@nestjs/axios';
import { ConfigService } from '@nestjs/config';
import { firstValueFrom } from 'rxjs';
import { DatabaseService } from '../../database/database.service';
import { Movie } from '../../common/interfaces/movie.interface';

@Injectable()
export class TmdbService {
  private readonly logger = new Logger(TmdbService.name);
  
  // configurazione api tmdb
  private readonly apiKey: string;
  private readonly baseUrl = 'https://api.themoviedb.org/3';
  
  // rate limiting per evitare ban
  private readonly maxRequestsPerWindow = 35;
  private readonly rateLimitWindow = 10000; // 10 secondi
  private requestHistory: number[] = [];

  constructor(
    private readonly httpService: HttpService,
    private readonly configService: ConfigService,
    private readonly databaseService: DatabaseService,
  ) {
    // carica api key da .env
    this.apiKey = this.configService.get<string>('TMDB_API_KEY');
  }

  // ===== enrichment singolo film =====

  /**
   * arricchisce film con tutti i dati tmdb disponibili
   * cerca per titolo+anno, recupera dettagli completi, salva in database
   */
  async enrichMovie(movie: Movie): Promise<Movie | null> {
    try {
      await this.enforceRateLimit();

      // step 1: cerca film su tmdb per titolo e anno
      const searchResult = await this.searchByTitle(movie.title, movie.year);
      
      if (!searchResult || !searchResult.id) {
        this.logger.warn(`film non trovato su tmdb: ${movie.title}`);
        return null;
      }

      // step 2: recupera dettagli completi (cast, crew, keywords, videos)
      await this.enforceRateLimit();
      const detailsUrl = `${this.baseUrl}/movie/${searchResult.id}`;
      const detailsResponse = await firstValueFrom(
        this.httpService.get(detailsUrl, {
          params: {
            api_key: this.apiKey,
            language: 'it-IT',
            append_to_response: 'credits,keywords,videos,release_dates',
          },
        }),
      );

      const tmdbData = detailsResponse.data;

      // step 3: estrai regista dal crew
      const director = tmdbData.credits?.crew
        ?.find((c: any) => c.job === 'Director')?.name;

      // step 4: estrai top 10 attori dal cast
      const actors = tmdbData.credits?.cast
        ?.slice(0, 10)
        .map((a: any) => a.name) || [];

      // step 5: estrai keywords (max 10)
      const keywords = tmdbData.keywords?.keywords
        ?.slice(0, 10)
        .map((k: any) => k.name) || [];

      // step 6: estrai certificazione (rating eta)
      const certification = this.extractCertification(tmdbData);

      // step 7: estrai url trailer youtube
      const trailerUrl = this.extractTrailerUrl(tmdbData);

      // step 8: crea oggetto movie arricchito
      const enrichedMovie: Movie = {
        ...movie,
        tmdb_id: tmdbData.id,
        is_enriched: true,
        
        // generi
        genres: tmdbData.genres?.map((g: any) => g.name) || [],
        
        // persone
        director,
        actors,
        
        // testi
        overview: tmdbData.overview,
        tagline: tmdbData.tagline,
        
        // numeri
        runtime: tmdbData.runtime,
        tmdb_rating: tmdbData.vote_average,
        vote_count: tmdbData.vote_count,
        popularity: tmdbData.popularity,
        budget: tmdbData.budget,
        revenue: tmdbData.revenue,
        
        // immagini
        poster_url: tmdbData.poster_path
          ? `https://image.tmdb.org/t/p/w500${tmdbData.poster_path}`
          : undefined,
        backdrop_url: tmdbData.backdrop_path
          ? `https://image.tmdb.org/t/p/original${tmdbData.backdrop_path}`
          : undefined,
        
        // metadata
        status: tmdbData.status,
        production_companies: tmdbData.production_companies?.map((c: any) => c.name) || [],
        production_countries: tmdbData.production_countries?.map((c: any) => c.name) || [],
        original_language: tmdbData.original_language,
        original_title: tmdbData.original_title,
        spoken_languages: tmdbData.spoken_languages?.map((l: any) => l.english_name) || [],
        adult: tmdbData.adult,
        homepage: tmdbData.homepage,
        imdb_id: tmdbData.imdb_id,
        keywords,
        certification,
        trailer_url: trailerUrl,
      };

      this.logger.log(`film arricchito: ${enrichedMovie.title} (tmdb_id: ${enrichedMovie.tmdb_id})`);
      return enrichedMovie;

    } catch (error) {
      this.logger.error(`errore enrichment ${movie.title}: ${error.message}`);
      return null;
    }
  }

  // ===== ricerca film =====

  /**
   * cerca film su tmdb per titolo e anno
   * ritorna primo risultato piu rilevante
   */
  async searchByTitle(title: string, year?: number): Promise<any> {
    try {
      const url = `${this.baseUrl}/search/movie`;
      const response = await firstValueFrom(
        this.httpService.get(url, {
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

      // ritorna primo risultato (piu rilevante)
      return response.data.results[0];
    } catch (error) {
      this.logger.error(`errore ricerca tmdb: ${error.message}`);
      return null;
    }
  }

  // ===== sync film popolari =====

  /**
   * sincronizza top N film popolari da tmdb nel database
   * usato per popolare autocomplete al primo avvio
   */
  async syncPopularMovies(limit: number = 10000): Promise<{ synced: number; errors: number }> {
    this.logger.log(`sync ${limit} film popolari da tmdb...`);

    let synced = 0;
    let errors = 0;
    const batchSize = 20; // film per pagina
    const totalPages = Math.ceil(limit / batchSize);

    for (let page = 1; page <= totalPages; page++) {
      try {
        await this.enforceRateLimit();

        const url = `${this.baseUrl}/movie/popular`;
        const response = await firstValueFrom(
          this.httpService.get(url, {
            params: {
              api_key: this.apiKey,
              language: 'it-IT',
              page: page,
            },
          }),
        );

        if (!response.data.results || response.data.results.length === 0) {
          break;
        }

        // processa ogni film della pagina
        for (const tmdbMovie of response.data.results) {
          try {
            // verifica se gia esistente
            const existing = await this.databaseService.findMovieByTmdbId(tmdbMovie.id);
            if (existing) continue;

            // crea placeholder non arricchito (solo dati base)
            const movie: Movie = {
              id: `tmdb_${tmdbMovie.id}`,
              title: tmdbMovie.title,
              year: tmdbMovie.release_date
                ? new Date(tmdbMovie.release_date).getFullYear()
                : undefined,
              source: 'TMDB',
              tmdb_id: tmdbMovie.id,
              overview: tmdbMovie.overview,
              poster_url: tmdbMovie.poster_path
                ? `https://image.tmdb.org/t/p/w500${tmdbMovie.poster_path}`
                : undefined,
              popularity: tmdbMovie.popularity,
              tmdb_rating: tmdbMovie.vote_average,
              vote_count: tmdbMovie.vote_count,
              genres: [],
              actors: [],
              is_enriched: false, // sara arricchito on-demand
            };

            await this.databaseService.saveMovie(movie);
            synced++;

            if (synced % 100 === 0) {
              this.logger.log(`${synced} film sincronizzati...`);
            }

          } catch (error) {
            this.logger.error(`errore ${tmdbMovie.title}: ${error.message}`);
            errors++;
          }
        }

        // pausa tra pagine per rate limiting
        await new Promise(resolve => setTimeout(resolve, 300));

      } catch (error) {
        this.logger.error(`errore pagina ${page}: ${error.message}`);
        errors++;
      }
    }

    this.logger.log(`sync completato: ${synced} film, ${errors} errori`);
    return { synced, errors };
  }

  // ===== autocomplete =====

  /**
   * ricerca veloce per autocomplete dal database locale
   * no api calls, cerca direttamente in postgresql
   */
  async searchForAutocomplete(query: string, limit: number = 10): Promise<Movie[]> {
    try {
      return await this.databaseService.searchMoviesForAutocomplete(query, limit);
    } catch (error) {
      this.logger.error(`errore autocomplete: ${error.message}`);
      return [];
    }
  }

  // ===== helper methods =====

  /**
   * applica rate limiting: max 35 richieste per 10 secondi
   * attende se limite raggiunto
   */
  private async enforceRateLimit(): Promise<void> {
    const now = Date.now();
    
    // rimuovi richieste vecchie fuori dalla finestra
    this.requestHistory = this.requestHistory.filter(
      (timestamp) => now - timestamp < this.rateLimitWindow
    );

    // se limite raggiunto, attendi
    if (this.requestHistory.length >= this.maxRequestsPerWindow) {
      const oldestRequest = this.requestHistory[0];
      const waitTime = this.rateLimitWindow - (now - oldestRequest) + 100;
      
      this.logger.debug(`rate limit raggiunto, attesa ${waitTime}ms`);
      await new Promise((resolve) => setTimeout(resolve, waitTime));
    }

    // registra questa richiesta
    this.requestHistory.push(Date.now());
  }

  /**
   * estrae certificazione (rating eta) da dati tmdb
   */
  private extractCertification(tmdbData: any): string | undefined {
    try {
      const releases = tmdbData.release_dates?.results;
      if (!releases) return undefined;

      // cerca certificazione US o IT
      const usRelease = releases.find((r: any) => r.iso_3166_1 === 'US');
      const itRelease = releases.find((r: any) => r.iso_3166_1 === 'IT');

      const release = itRelease || usRelease;
      if (release && release.release_dates && release.release_dates.length > 0) {
        return release.release_dates[0].certification;
      }

      return undefined;
    } catch (error) {
      return undefined;
    }
  }

  /**
   * estrae url trailer youtube da dati tmdb
   */
  private extractTrailerUrl(tmdbData: any): string | undefined {
    try {
      const videos = tmdbData.videos?.results;
      if (!videos || videos.length === 0) return undefined;

      // cerca trailer ufficiale youtube
      const trailer = videos.find(
        (v: any) => v.type === 'Trailer' && v.site === 'YouTube'
      );

      if (trailer) {
        return `https://www.youtube.com/watch?v=${trailer.key}`;
      }

      return undefined;
    } catch (error) {
      return undefined;
    }
  }
}