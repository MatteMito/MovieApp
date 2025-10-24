// File: src/database/database.service.ts
// ✅ OTTIMIZZATO: rimossa logica tmdb_cache ridondante

import { Injectable, Logger } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository, FindOptionsWhere } from 'typeorm';
import { MovieEntity } from './entities/movie.entity';
import { Movie } from '../common/interfaces/movie.interface';

@Injectable()
export class DatabaseService {
  private readonly logger = new Logger(DatabaseService.name);
  private readonly analyticsCache = new Map<string, { data: any; expiresAt: Date }>();

  constructor(
    @InjectRepository(MovieEntity)
    private movieRepository: Repository<MovieEntity>,
  ) {}

  // ===== CONVERSIONI ENTITY <-> MODEL =====

  private movieToEntity(movie: Movie): MovieEntity {
    const entity = new MovieEntity();

    entity.id = movie.id;
    entity.title = movie.title;
    entity.year = movie.year;
    entity.source = movie.source as any;

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

    entity.is_enriched = !!(movie.tmdb_id && movie.tmdb_id > 0);
    
    return entity;
  }

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
    };
  }

  // ===== OPERAZIONI CRUD MOVIES =====

  async saveMovie(movie: Movie): Promise<Movie> {
    try {
      const entity = this.movieToEntity(movie);
      const saved = await this.movieRepository.save(entity);
      
      this.logger.debug(`💾 film salvato: ${movie.title}`);
      return this.entityToMovie(saved);
    } catch (error) {
      this.logger.error(`errore salvataggio film ${movie.title}: ${error.message}`);
      throw error;
    }
  }

  async saveMovies(movies: Movie[]): Promise<MovieEntity[]> {
    try {
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

      if (!entity) {
        return null;
      }

      return this.entityToMovie(entity);
    } catch (error) {
      this.logger.error(`errore ricerca film ${title}: ${error.message}`);
      return null;
    }
  }

  async updateMovie(id: string, updates: Partial<Movie>): Promise<Movie> {
    try {
      const entity = await this.movieRepository.findOne({ where: { id } });
      
      if (!entity) {
        throw new Error(`Movie ${id} not found`);
      }

      const updatedEntity = this.movieToEntity({
        ...this.entityToMovie(entity),
        ...updates,
      });

      const updated = await this.movieRepository.save(updatedEntity);
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

  // ===== ANALYTICS CACHE (in-memory) =====

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
    return this.movieRepository !== undefined;
  }
}