// File: src/database/database.service.ts
// AGGIORNATO: rimozione campi utente da MovieEntity

import { Injectable, Logger } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository, FindOptionsWhere } from 'typeorm';
import { MovieEntity } from './entities/movie.entity';
import { TmdbCacheEntity } from './entities/tmdb-cache.entity';
import { Movie } from '../common/interfaces/movie.interface';

@Injectable()
export class DatabaseService {
  private readonly logger = new Logger(DatabaseService.name);
  private readonly analyticsCache = new Map<string, { data: any; expiresAt: Date }>();

  constructor(
    @InjectRepository(MovieEntity)
    private movieRepository: Repository<MovieEntity>,
    
    @InjectRepository(TmdbCacheEntity)
    private tmdbCacheRepository: Repository<TmdbCacheEntity>,
  ) {}

  // ===== CONVERSIONI ENTITY <-> MODEL =====

  /**
   * Converte Movie model in MovieEntity (SENZA dati utente)
   */
  private movieToEntity(movie: Movie): MovieEntity {
    const entity = new MovieEntity();

    // Dati base
    entity.id = movie.id;
    entity.title = movie.title;
    entity.year = movie.year;
    entity.source = movie.source as any;

    // Dati TMDB
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
    entity.budget = movie.budget;
    entity.revenue = movie.revenue;
    entity.status = movie.status;
    entity.original_language = movie.original_language;
    entity.original_title = movie.original_title;
    entity.popularity = movie.popularity;
    entity.adult = movie.adult;
    entity.homepage = movie.homepage;
    entity.imdb_id = movie.imdb_id;
    entity.production_companies = movie.production_companies?.length > 0 ? movie.production_companies : [];
    entity.production_countries = movie.production_countries?.length > 0 ? movie.production_countries : [];
    entity.spoken_languages = movie.spoken_languages?.length > 0 ? movie.spoken_languages : [];
    entity.keywords = movie.keywords?.length > 0 ? movie.keywords : [];
    entity.certification = movie.certification;
    entity.trailer_url = movie.trailer_url;

    // Flag arricchimento
    entity.is_enriched = !!(movie.tmdb_id && movie.tmdb_id > 0);

    return entity;
  }

  /**
   * Converte MovieEntity in Movie model (SENZA dati utente)
   * I dati utente vengono gestiti da UserMoviesService
   */
  private entityToMovie(entity: MovieEntity): Movie {
    return {
      id: entity.id,
      title: entity.title,
      year: entity.year,
      source: entity.source as any,

      // ⚠️ RIMOSSI: user_rating, watched_date, user_review, is_watched
      // Questi dati sono ora in UserMovieEntity

      tmdb_id: entity.tmdb_id,
      genres: entity.genres || [],
      director: entity.director,
      actors: entity.actors || [],
      overview: entity.overview,
      tagline: entity.tagline,
      poster_url: entity.poster_url,
      backdrop_url: entity.backdrop_url,
      tmdb_rating: entity.tmdb_rating,
      vote_count: entity.vote_count,
      runtime: entity.runtime,
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
    };
  }

  // ===== GESTIONE FILM =====

  async saveMovie(movie: Movie): Promise<MovieEntity> {
    try {
      const movieEntity = this.movieToEntity(movie);
      movieEntity.is_enriched = !!(movieEntity.tmdb_id && movieEntity.tmdb_id > 0);
      
      const saved = await this.movieRepository.save(movieEntity);
      this.logger.debug(`film salvato: ${saved.title} (arricchito: ${saved.is_enriched})`);
      
      return saved;
    } catch (error) {
      this.logger.error(`errore salvataggio film ${movie.title}: ${error.message}`);
      throw error;
    }
  }

  async saveMovies(movies: Movie[]): Promise<MovieEntity[]> {
    try {
      this.logger.log(`📦 avvio salvataggio batch: ${movies.length} film`);

      if (movies.length === 0) {
        this.logger.warn('⚠️ nessun film da salvare');
        return [];
      }

      const entities = movies.map((movie) => {
        const entity = this.movieToEntity(movie);
        entity.is_enriched = !!(movie.tmdb_id && movie.tmdb_id > 0);
        return entity;
      });

      const chunkSize = 100;
      const savedEntities: MovieEntity[] = [];

      for (let i = 0; i < entities.length; i += chunkSize) {
        const chunk = entities.slice(i, i + chunkSize);
        const saved = await this.movieRepository.save(chunk);
        savedEntities.push(...saved);
        
        this.logger.log(`💾 salvati ${saved.length} film (chunk ${Math.floor(i / chunkSize) + 1})`);
      }

      const enrichedCount = savedEntities.filter((e) => e.is_enriched).length;
      this.logger.log(`✅ batch completato: ${savedEntities.length} film salvati (${enrichedCount} arricchiti)`);

      return savedEntities;
    } catch (error) {
      this.logger.error(`errore salvataggio batch: ${error.message}`);
      throw error;
    }
  }

  async getAllMovies(): Promise<Movie[]> {
    try {
      const entities = await this.movieRepository.find({
        order: { title: 'ASC' },
      });

      const movies = entities.map((entity) => this.entityToMovie(entity));
      const enrichedCount = movies.filter((m) => m.tmdb_id).length;

      this.logger.log(`📚 recuperati ${movies.length} film (${enrichedCount} arricchiti)`);

      return movies;
    } catch (error) {
      this.logger.error(`errore recupero film: ${error.message}`);
      return [];
    }
  }

  async getMovieById(id: string): Promise<Movie | null> {
    try {
      const entity = await this.movieRepository.findOne({ where: { id } });

      if (!entity) {
        this.logger.debug(`film non trovato per id: ${id}`);
        return null;
      }

      return this.entityToMovie(entity);
    } catch (error) {
      this.logger.error(`errore recupero film ${id}: ${error.message}`);
      return null;
    }
  }

  async getMoviesByIds(movieIds: string[]): Promise<Movie[]> {
    try {
      if (movieIds.length === 0) {
        return [];
      }

      const entities = await this.movieRepository.findByIds(movieIds);
      const movies = entities.map((entity) => this.entityToMovie(entity));

      this.logger.debug(`📚 recuperati ${movies.length} film da ${movieIds.length} IDs`);

      return movies;
    } catch (error) {
      this.logger.error(`errore recupero film by IDs: ${error.message}`);
      return [];
    }
  }

  async findMovieByTitleYear(title: string, year?: number): Promise<Movie | null> {
    try {
      const whereConditions: FindOptionsWhere<MovieEntity> = { title };
      if (year) whereConditions.year = year;

      let entity = await this.movieRepository.findOne({
        where: whereConditions,
      });

      if (!entity && title) {
        entity = await this.movieRepository
          .createQueryBuilder('movie')
          .where('LOWER(movie.title) = LOWER(:title)', { title })
          .andWhere(year ? 'movie.year = :year' : '1=1', { year })
          .getOne();
      }

      if (entity) {
        this.logger.debug(`film trovato: ${title} (${year}) - arricchito: ${entity.is_enriched}`);
        return this.entityToMovie(entity);
      }

      return null;
    } catch (error) {
      this.logger.error(`errore ricerca film ${title}: ${error.message}`);
      return null;
    }
  }

  async getUnenrichedMovies(): Promise<Movie[]> {
    try {
      const entities = await this.movieRepository.find({
        where: { is_enriched: false },
        order: { created_at: 'ASC' },
      });

      const movies = entities.map((entity) => this.entityToMovie(entity));
      this.logger.log(`📊 trovati ${movies.length} film da arricchire`);

      return movies;
    } catch (error) {
      this.logger.error(`errore recupero film non arricchiti: ${error.message}`);
      return [];
    }
  }

  async updateMovie(id: string, updates: Partial<Movie>): Promise<Movie> {
    try {
      const entity = await this.movieRepository.findOne({ where: { id } });
      
      if (!entity) {
        throw new Error(`Movie ${id} not found`);
      }

      Object.assign(entity, this.movieToEntity({ ...this.entityToMovie(entity), ...updates }));
      
      const updated = await this.movieRepository.save(entity);
      this.logger.debug(`✅ film ${id} aggiornato`);
      
      return this.entityToMovie(updated);
    } catch (error) {
      this.logger.error(`errore aggiornamento film ${id}: ${error.message}`);
      throw error;
    }
  }

  async deleteMovie(id: string): Promise<void> {
    try {
      const entity = await this.movieRepository.findOne({ where: { id } });
      
      if (!entity) {
        throw new Error(`Movie ${id} not found`);
      }

      await this.movieRepository.remove(entity);
      this.logger.debug(`✅ film ${id} eliminato`);
    } catch (error) {
      this.logger.error(`errore eliminazione film ${id}: ${error.message}`);
      throw error;
    }
  }

  async getMovieByTmdbId(tmdbId: number): Promise<Movie | null> {
    try {
      const entity = await this.movieRepository.findOne({ 
        where: { tmdb_id: tmdbId } 
      });

      if (!entity) {
        return null;
      }

      return this.entityToMovie(entity);
    } catch (error) {
      this.logger.error(`errore recupero film tmdb_id ${tmdbId}: ${error.message}`);
      return null;
    }
  }

  // ===== TMDB CACHE =====

  async saveTmdbCache(cacheKey: string, tmdbData: any): Promise<void> {
    try {
      const entity = new TmdbCacheEntity();
      entity.cache_key = cacheKey;
      entity.tmdb_id = tmdbData.id;
      entity.tmdb_data = tmdbData;
      entity.title = tmdbData.title;
      entity.year = tmdbData.release_date?.substring(0, 4);
      entity.imdb_id = tmdbData.imdb_id;
      entity.expires_at = new Date(Date.now() + 30 * 24 * 60 * 60 * 1000);

      await this.tmdbCacheRepository.save(entity);
      this.logger.debug(`💾 cache tmdb salvata: ${cacheKey}`);
    } catch (error) {
      this.logger.error(`errore salvataggio cache ${cacheKey}: ${error.message}`);
    }
  }

  async getTmdbCache(cacheKey: string): Promise<any | null> {
    try {
      const cached = await this.tmdbCacheRepository.findOne({
        where: { cache_key: cacheKey },
      });

      if (!cached) {
        return null;
      }

      if (cached.expires_at && cached.expires_at < new Date()) {
        await this.tmdbCacheRepository.remove(cached);
        this.logger.debug(`🗑️ cache scaduta rimossa: ${cacheKey}`);
        return null;
      }

      cached.hit_count = (cached.hit_count || 0) + 1;
      cached.last_accessed_at = new Date();
      await this.tmdbCacheRepository.save(cached);

      this.logger.debug(`✅ cache hit: ${cacheKey}`);
      return cached.tmdb_data;
    } catch (error) {
      this.logger.error(`errore recupero cache ${cacheKey}: ${error.message}`);
      return null;
    }
  }

  async getCachedTmdbData(title: string, year?: number): Promise<any | null> {
    try {
      const cacheKey = `${title.toLowerCase()}_${year || 'unknown'}`;
      return await this.getTmdbCache(cacheKey);
    } catch (error) {
      this.logger.error(`errore recupero cache per ${title}: ${error.message}`);
      return null;
    }
  }

  // ===== ANALYTICS CACHE =====

  async saveAnalytics(userId: string, analyticsData: any): Promise<void> {
    try {
      const expiresAt = new Date(Date.now() + 24 * 60 * 60 * 1000);
      this.analyticsCache.set(userId, { data: analyticsData, expiresAt });
      this.logger.log(`💾 analytics salvate per utente: ${userId}`);
    } catch (error) {
      this.logger.error(`errore salvataggio analytics ${userId}: ${error.message}`);
      throw error;
    }
  }

  async getAnalytics(userId: string): Promise<any | null> {
    try {
      const cached = this.analyticsCache.get(userId);

      if (!cached) return null;

      if (cached.expiresAt < new Date()) {
        this.analyticsCache.delete(userId);
        return null;
      }

      return cached.data;
    } catch (error) {
      this.logger.error(`errore recupero analytics ${userId}: ${error.message}`);
      return null;
    }
  }

  isDatabaseAvailable(): boolean {
    return this.movieRepository !== undefined && this.tmdbCacheRepository !== undefined;
  }
}