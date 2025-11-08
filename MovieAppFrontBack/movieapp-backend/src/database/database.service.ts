// service database con metodi per sync tmdb e deduplicazione film

import { Injectable, Logger } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository, FindOptionsWhere, Not, IsNull } from 'typeorm';
import { MovieEntity } from './entities/movie.entity';
import { Movie } from '../common/interfaces/movie.interface';

@Injectable()
export class DatabaseService {
  private readonly logger = new Logger(DatabaseService.name);
  // cache in-memory per analytics (evita query ripetute)
  private readonly analyticsCache = new Map<string, { data: any; expiresAt: Date }>();

  constructor(
    @InjectRepository(MovieEntity)
    private movieRepository: Repository<MovieEntity>,
  ) {}

  // ===== conversioni entity <-> model =====

  // converte model movie in entity typeorm per il salvataggio
  private movieToEntity(movie: Movie): MovieEntity {
    const entity = new MovieEntity();

    // dati base
    entity.id = movie.id;
    entity.title = movie.title;
    entity.year = movie.year;
    entity.source = movie.source as any;

    // dati tmdb
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

  // converte entity typeorm in model movie per il ritorno
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

  // salva o aggiorna un film con deduplicazione automatica
  async saveMovie(movie: Movie): Promise<MovieEntity> {
    try {
      // step 1: cerca per id originale
      let entity = await this.movieRepository.findOne({
        where: { id: movie.id },
      });

      // step 2: se non trovato per id, cerca per titolo+anno+source (deduplicazione)
      if (!entity && movie.title && movie.year) {
        entity = await this.movieRepository.findOne({
          where: {
            title: movie.title,
            year: movie.year,
            source: movie.source,
          },
        });

        if (entity) {
          this.logger.log(`film duplicato trovato: ${movie.title} (${movie.year}) - uso id esistente: ${entity.id}`);
          // aggiorna movie.id per usare l'id esistente
          movie.id = entity.id;
        }
      }

      if (entity) {
        // aggiorna entity esistente con nuovi dati
        Object.assign(entity, {
          title: movie.title,
          year: movie.year,
          source: movie.source,
          tmdb_id: movie.tmdb_id,
          is_enriched: movie.is_enriched,
          genres: movie.genres,
          director: movie.director,
          actors: movie.actors,
          overview: movie.overview,
          tagline: movie.tagline,
          runtime: movie.runtime,
          poster_url: movie.poster_url,
          backdrop_url: movie.backdrop_url,
          tmdb_rating: movie.tmdb_rating,
          vote_count: movie.vote_count,
          popularity: movie.popularity,
          budget: movie.budget,
          revenue: movie.revenue,
          status: movie.status,
          production_companies: movie.production_companies,
          production_countries: movie.production_countries,
          original_language: movie.original_language,
          original_title: movie.original_title,
          spoken_languages: movie.spoken_languages,
          adult: movie.adult,
          homepage: movie.homepage,
          imdb_id: movie.imdb_id,
          keywords: movie.keywords,
          certification: movie.certification,
          trailer_url: movie.trailer_url,
        });
      } else {
        // crea nuovo record
        entity = this.movieRepository.create({
          id: movie.id,
          title: movie.title,
          year: movie.year,
          source: movie.source,
          tmdb_id: movie.tmdb_id,
          is_enriched: movie.is_enriched || false,
          genres: movie.genres || [],
          director: movie.director,
          actors: movie.actors || [],
          overview: movie.overview,
          tagline: movie.tagline,
          runtime: movie.runtime,
          poster_url: movie.poster_url,
          backdrop_url: movie.backdrop_url,
          tmdb_rating: movie.tmdb_rating,
          vote_count: movie.vote_count,
          popularity: movie.popularity,
          budget: movie.budget,
          revenue: movie.revenue,
          status: movie.status,
          production_companies: movie.production_companies || [],
          production_countries: movie.production_countries || [],
          original_language: movie.original_language,
          original_title: movie.original_title,
          spoken_languages: movie.spoken_languages || [],
          adult: movie.adult,
          homepage: movie.homepage,
          imdb_id: movie.imdb_id,
          keywords: movie.keywords || [],
          certification: movie.certification,
          trailer_url: movie.trailer_url,
        });
      }

      return await this.movieRepository.save(entity);
    } catch (error) {
      this.logger.error(`errore save movie: ${error.message}`);
      throw error;
    }
  }

  // salva batch di film con deduplicazione
  async saveMovies(movies: Movie[]): Promise<MovieEntity[]> {
    try {
      const savedEntities: MovieEntity[] = [];

      // salva uno per uno per gestire deduplicazione
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

  // cerca film per id
  async findMovieById(id: string): Promise<Movie | null> {
    try {
      const entity = await this.movieRepository.findOne({ where: { id } });
      return entity ? this.entityToMovie(entity) : null;
    } catch (error) {
      this.logger.error(`errore ricerca film ${id}: ${error.message}`);
      return null;
    }
  }

  // cerca film per titolo e opzionalmente anno
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

  // cerca film per tmdb id
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

  // recupera tutti i film ordinati per titolo
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

  // statistiche database: totali, arricchiti, percentuali
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
            ? Math.round((enrichedMovies / totalMovies) * 100)
            : 0,
      };
    } catch (error) {
      this.logger.error(`errore stats: ${error.message}`);
      throw error;
    }
  }

  // alias per retrocompatibilità
  async getSyncStats() {
    return this.getStats();
  }

  // ricerca film per autocomplete con ranking per popolarità
  async searchMoviesForAutocomplete(query: string, limit: number = 10): Promise<Movie[]> {
    try {
      const entities = await this.movieRepository
        .createQueryBuilder('movie')
        .where('LOWER(movie.title) LIKE LOWER(:query)', {
          query: `%${query}%`,
        })
        .orderBy('movie.popularity', 'DESC', 'NULLS LAST') // prima i più popolari
        .addOrderBy('movie.title', 'ASC') // poi alfabetico
        .limit(limit)
        .getMany();

      return entities.map((entity) => this.entityToMovie(entity));
    } catch (error) {
      this.logger.error(`errore autocomplete search: ${error.message}`);
      return [];
    }
  }

  // elimina tutti i film dal database
  async deleteAllMovies(): Promise<void> {
    try {
      await this.movieRepository.clear();
      this.logger.log('tutti i film eliminati dal database');
    } catch (error) {
      this.logger.error(`errore eliminazione film: ${error.message}`);
      throw error;
    }
  }

  // ===== analytics cache management =====

  // recupera dati analytics dalla cache se non scaduti
  getCachedAnalytics(key: string): any {
    const cached = this.analyticsCache.get(key);
    if (cached && cached.expiresAt > new Date()) {
      return cached.data;
    }
    return null;
  }

  // salva dati analytics in cache con ttl
  setCachedAnalytics(key: string, data: any, ttlMinutes: number = 5): void {
    const expiresAt = new Date();
    expiresAt.setMinutes(expiresAt.getMinutes() + ttlMinutes);
    this.analyticsCache.set(key, { data, expiresAt });
  }

  // pulisce tutta la cache analytics
  clearAnalyticsCache(): void {
    this.analyticsCache.clear();
  }
}