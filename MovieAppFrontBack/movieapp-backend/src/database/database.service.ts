//service principale per interazioni con database postgresql
//gestisce crud film, conversioni entity-model, deduplicazione, cache analytics

import { Injectable, Logger } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository, FindOptionsWhere, Not, IsNull, ILike } from 'typeorm';
import { MovieEntity } from './entities/movie.entity';
import { Movie } from '../common/interfaces/movie.interface';

@Injectable()
export class DatabaseService {
  private readonly logger = new Logger(DatabaseService.name);
  
  //cache in-memory per analytics (evita ricalcoli frequenti)
  private readonly analyticsCache = new Map<string, { data: any; expiresAt: Date }>();

  constructor(
    //inietta repository typeorm per accesso diretto al database
    @InjectRepository(MovieEntity)
    private movieRepository: Repository<MovieEntity>,
  ) {}

  //===== metodi privati di conversione entity <-> model =====

  /**
   * converte oggetto movie (interfaccia) in movieentity (database)
   * gestisce tutti i campi opzionali e array vuoti
   */
  private movieToEntity(movie: Movie): MovieEntity {
    const entity = new MovieEntity();

    //dati base obbligatori
    entity.id = movie.id;
    entity.title = movie.title;
    entity.year = movie.year;
    entity.source = movie.source as any;

    //dati tmdb (opzionali)
    entity.tmdb_id = movie.tmdb_id;
    entity.genres = movie.genres?.length > 0 ? movie.genres : [];
    entity.director = movie.director;
    entity.actors = movie.actors?.length > 0 ? movie.actors : undefined;
    entity.overview = movie.overview;
    entity.tagline = movie.tagline;
    entity.poster_url = movie.poster_url;
    entity.backdrop_url = movie.backdrop_url;
    entity.tmdb_rating = movie.tmdb_rating;
    entity.vote_count = movie.vote_count;
    entity.runtime = movie.runtime;
    
    //dati finanziari
    entity.budget = movie.budget;
    entity.revenue = movie.revenue;
    entity.status = movie.status;
    
    //metadata
    entity.original_language = movie.original_language;
    entity.original_title = movie.original_title;
    entity.popularity = movie.popularity;
    entity.adult = movie.adult;
    entity.homepage = movie.homepage;
    entity.imdb_id = movie.imdb_id;
    
    //array (gestisci vuoti correttamente)
    entity.production_companies = movie.production_companies?.length > 0 ? movie.production_companies : [];
    entity.production_countries = movie.production_countries?.length > 0 ? movie.production_countries : [];
    entity.spoken_languages = movie.spoken_languages?.length > 0 ? movie.spoken_languages : [];
    entity.keywords = movie.keywords?.length > 0 ? movie.keywords : [];
    
    //altri campi
    entity.certification = movie.certification;
    entity.trailer_url = movie.trailer_url;

    //flag enrichment
    entity.is_enriched = movie.is_enriched || false;
    
    return entity;
  }

  /**
   * converte movieentity (database) in oggetto movie (interfaccia)
   * assicura array vuoti invece di null per consistenza
   */
  private entityToMovie(entity: MovieEntity): Movie {
    return {
      id: entity.id,
      title: entity.title,
      year: entity.year,
      source: entity.source,
      tmdb_id: entity.tmdb_id,
      director: entity.director,
      genres: entity.genres || [],
      actors: entity.actors || [],
      overview: entity.overview,
      tagline: entity.tagline,
      runtime: entity.runtime,
      poster_url: entity.poster_url,
      backdrop_url: entity.backdrop_url,
      tmdb_rating: entity.tmdb_rating,
      vote_count: entity.vote_count,
      budget: entity.budget,
      revenue: entity.revenue,
      status: entity.status,
      original_language: entity.original_language,
      original_title: entity.original_title,
      popularity: entity.popularity,
      adult: entity.adult,
      homepage: entity.homepage,
      imdb_id: entity.imdb_id,
      production_companies: entity.production_companies || [],
      production_countries: entity.production_countries || [],
      spoken_languages: entity.spoken_languages || [],
      keywords: entity.keywords || [],
      certification: entity.certification,
      trailer_url: entity.trailer_url,
      created_at: entity.created_at,
      updated_at: entity.updated_at,
      is_enriched: entity.is_enriched,
    };
  }

  //===== crud operations =====

  /**
   * salva o aggiorna film nel database con deduplicazione intelligente
   * step 1: cerca per id originale
   * step 2: se non trovato, cerca per titolo+anno+source (evita duplicati)
   * step 3: aggiorna esistente o crea nuovo
   */
  async saveMovie(movie: Movie): Promise<MovieEntity> {
    try {
      //step 1: cerca per id originale (es: letterboxd_inception_2010)
      let entity = await this.movieRepository.findOne({
        where: { id: movie.id },
      });

      //step 2: se non trovato per id, cerca per titolo+anno+source
      //previene duplicati quando stesso film importato da fonti diverse
      if (!entity && movie.title && movie.year) {
        entity = await this.movieRepository.findOne({
          where: {
            title: movie.title,
            year: movie.year,
            source: movie.source as any,
          },
        });
      }

      //step 3: aggiorna entity esistente o crea nuova
      if (entity) {
        //aggiorna solo se il film ha piu dati (es: dopo enrichment)
        //merge intelligente: mantieni dati migliori tra esistente e nuovo
        Object.assign(entity, {
          tmdb_id: movie.tmdb_id || entity.tmdb_id,
          genres: movie.genres?.length > 0 ? movie.genres : entity.genres || [],
          director: movie.director || entity.director,
          actors: movie.actors?.length > 0 ? movie.actors : entity.actors || [],
          overview: movie.overview || entity.overview,
          tagline: movie.tagline || entity.tagline,
          runtime: movie.runtime || entity.runtime,
          poster_url: movie.poster_url || entity.poster_url,
          backdrop_url: movie.backdrop_url || entity.backdrop_url,
          tmdb_rating: movie.tmdb_rating || entity.tmdb_rating,
          vote_count: movie.vote_count || entity.vote_count,
          popularity: movie.popularity || entity.popularity,
          budget: movie.budget || entity.budget,
          revenue: movie.revenue || entity.revenue,
          status: movie.status || entity.status,
          production_companies: movie.production_companies?.length > 0 ? movie.production_companies : entity.production_companies || [],
          production_countries: movie.production_countries?.length > 0 ? movie.production_countries : entity.production_countries || [],
          original_language: movie.original_language || entity.original_language,
          original_title: movie.original_title || entity.original_title,
          spoken_languages: movie.spoken_languages?.length > 0 ? movie.spoken_languages : entity.spoken_languages || [],
          adult: movie.adult !== undefined ? movie.adult : entity.adult,
          homepage: movie.homepage || entity.homepage,
          imdb_id: movie.imdb_id || entity.imdb_id,
          keywords: movie.keywords?.length > 0 ? movie.keywords : entity.keywords || [],
          certification: movie.certification || entity.certification,
          trailer_url: movie.trailer_url || entity.trailer_url,
          is_enriched: movie.is_enriched || entity.is_enriched,
        });
      } else {
        //nessun film esistente, crea nuovo
        entity = this.movieToEntity(movie);
      }

      //salva nel database (insert o update)
      return await this.movieRepository.save(entity);
    } catch (error) {
      this.logger.error(`errore save movie: ${error.message}`);
      throw error;
    }
  }

  /**
   * salva batch di film gestendo deduplicazione per ognuno
   */
  async saveMovies(movies: Movie[]): Promise<MovieEntity[]> {
    try {
      const savedEntities: MovieEntity[] = [];

      //salva uno per uno per gestire deduplicazione correttamente
      //batch insert di typeorm non gestisce logica custom
      for (const movie of movies) {
        const entity = await this.saveMovie(movie);
        savedEntities.push(entity);
      }

      this.logger.log(`salvati ${savedEntities.length} film nel database (con deduplicazione)`);
      return savedEntities;
    } catch (error) {
      this.logger.error(`errore salvataggio batch: ${error.message}`);
      throw error;
    }
  }

  /**
   * cerca film per id univoco
   */
  async findMovieById(id: string): Promise<Movie | null> {
    try {
      const entity = await this.movieRepository.findOne({ where: { id } });
      return entity ? this.entityToMovie(entity) : null;
    } catch (error) {
      this.logger.error(`errore ricerca film ${id}: ${error.message}`);
      return null;
    }
  }

  /**
   * cerca film per titolo e anno (opzionale)
   */
  async findMovieByTitleYear(title: string, year?: number): Promise<Movie | null> {
    try {
      const where: FindOptionsWhere<MovieEntity> = { title };
      if (year) {
        where.year = year;
      }

      const entity = await this.movieRepository.findOne({ where });
      return entity ? this.entityToMovie(entity) : null;
    } catch (error) {
      this.logger.error(`errore ricerca ${title}: ${error.message}`);
      return null;
    }
  }

  /**
   * cerca film per tmdb id
   */
  async findMovieByTmdbId(tmdbId: number): Promise<Movie | null> {
    try {
      const entity = await this.movieRepository.findOne({
        where: { tmdb_id: tmdbId },
      });

      return entity ? this.entityToMovie(entity) : null;
    } catch (error) {
      this.logger.error(`errore ricerca tmdb ${tmdbId}: ${error.message}`);
      return null;
    }
  }

  /**
   * recupera tutti i film ordinati per titolo
   */
  async getAllMovies(): Promise<Movie[]> {
    try {
      const entities = await this.movieRepository.find({
        order: { title: 'ASC' },
      });

      return entities.map((entity) => this.entityToMovie(entity));
    } catch (error) {
      this.logger.error(`errore recupero tutti i film: ${error.message}`);
      throw error;
    }
  }

  /**
   * ricerca film per autocomplete (ricerca case-insensitive su titolo)
   * usato per suggerimenti durante digitazione
   */
  async searchMoviesForAutocomplete(query: string, limit: number = 10): Promise<Movie[]> {
    try {
      const entities = await this.movieRepository.find({
        where: {
          title: ILike(`%${query}%`), //ricerca case-insensitive
        },
        order: {
          popularity: 'DESC', //ordina per popolarita (film piu noti prima)
        },
        take: limit,
      });

      return entities.map((entity) => this.entityToMovie(entity));
    } catch (error) {
      this.logger.error(`errore ricerca autocomplete: ${error.message}`);
      return [];
    }
  }

  /**
   * ottieni statistiche database
   * ritorna conteggi film totali, arricchiti, tasso enrichment
   */
  async getStats() {
    try {
      const totalMovies = await this.movieRepository.count();
      const enrichedMovies = await this.movieRepository.count({
        where: { is_enriched: true },
      });
      const withTmdbId = await this.movieRepository.count({
        where: { tmdb_id: Not(IsNull()) },
      });

      return {
        totalMovies,
        enrichedMovies,
        notEnriched: totalMovies - enrichedMovies,
        withTmdbId,
        enrichmentRate:
          totalMovies > 0
            ? ((enrichedMovies / totalMovies) * 100).toFixed(2) + '%'
            : '0%',
      };
    } catch (error) {
      this.logger.error(`errore recupero stats: ${error.message}`);
      throw error;
    }
  }

  /**
   * ottieni statistiche per sync tmdb (alias di getStats)
   */
  async getSyncStats() {
    return this.getStats();
  }

  //===== cache analytics =====

  /**
   * salva analytics in cache con scadenza
   */
  setCachedAnalytics(userId: string, data: any, expiresInMinutes: number = 30) {
    const expiresAt = new Date(Date.now() + expiresInMinutes * 60 * 1000);
    this.analyticsCache.set(userId, { data, expiresAt });
  }

  /**
   * recupera analytics da cache se non scadute
   */
  getCachedAnalytics(userId: string): any | null {
    const cached = this.analyticsCache.get(userId);
    
    if (!cached) return null;
    
    if (new Date() > cached.expiresAt) {
      this.analyticsCache.delete(userId);
      return null;
    }
    
    return cached.data;
  }

  /**
   * pulisce cache analytics scadute
   */
  cleanExpiredCache() {
    const now = new Date();
    for (const [userId, cached] of this.analyticsCache.entries()) {
      if (now > cached.expiresAt) {
        this.analyticsCache.delete(userId);
      }
    }
  }
}