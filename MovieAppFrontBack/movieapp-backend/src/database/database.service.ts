//file: src/database/database.service.ts
//service database con metodi per sync tmdb

import { Injectable, Logger } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository, FindOptionsWhere, Not, IsNull } from 'typeorm';
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

  //===== conversioni entity <-> model =====

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

    entity.is_enriched = movie.is_enriched || false;
    
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
      is_enriched: entity.is_enriched,
    };
  }

  //===== operazioni crud movies =====

  async saveMovie(movie: Movie): Promise<Movie> {
    try {
      //controlla se esiste gia un film con questo tmdb_id
      if (movie.tmdb_id) {
        const existing = await this.movieRepository.findOne({
          where: { tmdb_id: movie.tmdb_id }
        });

        if (existing) {
          //aggiorna film esistente invece di inserirne uno nuovo
          this.logger.debug(`aggiornamento film esistente: ${movie.title} (tmdb_id: ${movie.tmdb_id})`);
          
          const entity = this.movieToEntity({
            ...movie,
            id: existing.id, //usa id esistente
          });
          
          const saved = await this.movieRepository.save(entity);
          return this.entityToMovie(saved);
        }
      }

      //film nuovo, inseriscilo
      const entity = this.movieToEntity(movie);
      const saved = await this.movieRepository.save(entity);
      
      this.logger.debug(`film salvato: ${movie.title}`);
      return this.entityToMovie(saved);
    } catch (error) {
      this.logger.error(`errore salvataggio film ${movie.title}: ${error.message}`);
      throw error;
    }
  }

  async saveMovies(movies: Movie[]): Promise<MovieEntity[]> {
    try {
      const entities = movies.map((movie) => this.movieToEntity(movie));

      const saved = await this.movieRepository.save(entities, { chunk: 100 });
      this.logger.log(`salvati ${saved.length} film nel database`);
      return saved;
    } catch (error) {
      this.logger.error(`errore salvataggio batch: ${error.message}`);
      throw error;
    }
  }

  async findMovieById(id: string): Promise<Movie | null> {
    try {
      const entity = await this.movieRepository.findOne({ where: { id } });
      return entity ? this.entityToMovie(entity) : null;
    } catch (error) {
      this.logger.error(`errore ricerca film ${id}: ${error.message}`);
      return null;
    }
  }

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

  async findMovieByTmdbId(tmdbId: number): Promise<Movie | null> {
    try {
      const entity = await this.movieRepository.findOne({
        where: { tmdb_id: tmdbId },
      });

      return entity ? this.entityToMovie(entity) : null;
    } catch (error) {
      this.logger.error(`errore ricerca tmdb_id ${tmdbId}: ${error.message}`);
      return null;
    }
  }

  async getAllMovies(): Promise<Movie[]> {
    try {
      const entities = await this.movieRepository.find();
      return entities.map(entity => this.entityToMovie(entity));
    } catch (error) {
      this.logger.error(`errore caricamento film: ${error.message}`);
      return [];
    }
  }

  async deleteAllMovies(): Promise<number> {
    try {
      const result = await this.movieRepository.delete({});
      const count = result.affected || 0;
      this.logger.log(`eliminati ${count} film dal database`);
      return count;
    } catch (error) {
      this.logger.error(`errore eliminazione film: ${error.message}`);
      throw error;
    }
  }

  async getMoviesCount(): Promise<number> {
    try {
      return await this.movieRepository.count();
    } catch (error) {
      this.logger.error(`errore conteggio film: ${error.message}`);
      return 0;
    }
  }

  //===== metodi per autocomplete e sync =====

  async searchMoviesForAutocomplete(query: string, limit: number = 10): Promise<Movie[]> {
    try {
      const normalized = query.toLowerCase().trim();

      const entities = await this.movieRepository
        .createQueryBuilder('movie')
        .where('LOWER(movie.title) LIKE :query', { query: `%${normalized}%` })
        .orderBy('movie.popularity', 'DESC', 'NULLS LAST')
        .addOrderBy('movie.year', 'DESC', 'NULLS LAST')
        .limit(limit)
        .getMany();

      return entities.map(entity => this.entityToMovie(entity));
    } catch (error) {
      this.logger.error(`errore autocomplete: ${error.message}`);
      return [];
    }
  }

  async getSyncStats(): Promise<{
    total: number;
    enriched: number;
    notEnriched: number;
    withTmdbId: number;
  }> {
    try {
      const [total, enriched, withTmdbId] = await Promise.all([
        this.movieRepository.count(),
        this.movieRepository.count({ where: { is_enriched: true } }),
        this.movieRepository.count({ where: { tmdb_id: Not(IsNull()) } }),
      ]);

      return {
        total,
        enriched,
        notEnriched: total - enriched,
        withTmdbId,
      };
    } catch (error) {
      this.logger.error(`errore stats sync: ${error.message}`);
      throw error;
    }
  }

  //===== cache analytics =====

  setAnalyticsCache(key: string, data: any, ttlMinutes: number = 30): void {
    const expiresAt = new Date();
    expiresAt.setMinutes(expiresAt.getMinutes() + ttlMinutes);
    
    this.analyticsCache.set(key, { data, expiresAt });
    this.logger.debug(`cache analytics salvata: ${key} (ttl: ${ttlMinutes}min)`);
  }

  getAnalyticsCache(key: string): any | null {
    const cached = this.analyticsCache.get(key);
    
    if (!cached) {
      return null;
    }

    if (new Date() > cached.expiresAt) {
      this.analyticsCache.delete(key);
      this.logger.debug(`cache analytics scaduta: ${key}`);
      return null;
    }

    this.logger.debug(`cache analytics hit: ${key}`);
    return cached.data;
  }

  clearAnalyticsCache(): void {
    this.analyticsCache.clear();
    this.logger.log('cache analytics svuotata');
  }
}