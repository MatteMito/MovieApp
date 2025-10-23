// gestione operazioni database centralizzata con flag is_enriched

import { Injectable, Logger, OnModuleInit } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository, FindOptionsWhere } from 'typeorm';
import { MovieEntity } from './entities/movie.entity';
import { TmdbCacheEntity } from './entities/tmdb-cache.entity';
import { Movie, DataSource } from '../common/interfaces/movie.interface';

// interfaccia tracking enrichment
interface EnrichmentStatus {
  sessionId: string;
  total: number;
  processed: number;
  successful: number;
  failed: number;
  startTime: string;
  currentMovie?: string;
  isCompleted: boolean;
}

@Injectable()
export class DatabaseService implements OnModuleInit {
  private readonly logger = new Logger(DatabaseService.name);
  
  // cache in-memory per sessioni e analytics
  private enrichmentSessions: Map<string, EnrichmentStatus> = new Map();
  private analyticsCache: Map<string, { data: any; expiresAt: Date }> = new Map();

  constructor(
    @InjectRepository(MovieEntity)
    private movieRepository: Repository<MovieEntity>,
    @InjectRepository(TmdbCacheEntity)
    private tmdbCacheRepository: Repository<TmdbCacheEntity>,
  ) {}

  // lifecycle hooks

  /**
   * inizializzazione modulo: verifica connettività e pulizia cache
   */
  async onModuleInit() {
    try {
      const movieCount = await this.movieRepository.count();
      const cacheCount = await this.tmdbCacheRepository.count();
      
      // statistiche enrichment con nuovo flag
      const enrichedCount = await this.movieRepository.count({ 
        where: { is_enriched: true } 
      });

      this.logger.log(`✅ database connesso`);
      this.logger.log(`film totali: ${movieCount}`);
      this.logger.log(`film arricchiti: ${enrichedCount}`);
      this.logger.log(`cache tmdb: ${cacheCount} entries`);

      // pulizia automatica cache scaduta
      await this.cleanExpiredTmdbCache();
    } catch (error) {
      this.logger.error(`❌ errore connessione database: ${error.message}`);
      throw error;
    }
  }

  // GESTIONE CACHE TMDB (ottimizzata)

  /**
   * recupera dati da cache tmdb
   * verifica scadenza e incrementa contatore hit
   */
  async getTmdbCache(cacheKey: string): Promise<any | null> {
    try {
      const cached = await this.tmdbCacheRepository.findOne({
        where: { cache_key: cacheKey },
      });

      if (!cached) {
        this.logger.debug(`cache miss: ${cacheKey}`);
        return null;
      }

      // verifica scadenza
      if (cached.isExpired()) {
        this.logger.debug(`cache expired: ${cacheKey}, rimozione automatica`);
        await this.tmdbCacheRepository.remove(cached);
        return null;
      }

      // aggiorna statistiche utilizzo
      cached.incrementHit();
      await this.tmdbCacheRepository.save(cached);

      this.logger.debug(`✅ cache hit: ${cacheKey} (hits: ${cached.hit_count})`);
      return cached.tmdb_data;
    } catch (error) {
      this.logger.error(`errore recupero cache ${cacheKey}: ${error.message}`);
      return null;
    }
  }

  /**
   * salva dati tmdb in cache con metadata automatici
   */
  async saveTmdbCache(cacheKey: string, tmdbData: any): Promise<void> {
    try {
      const cacheEntry = new TmdbCacheEntity();
      cacheEntry.cache_key = cacheKey;
      cacheEntry.tmdb_data = tmdbData;
      cacheEntry.tmdb_id = tmdbData.id;
      cacheEntry.title = tmdbData.title;
      cacheEntry.year = tmdbData.release_date
        ? new Date(tmdbData.release_date).getFullYear()
        : undefined;
      cacheEntry.imdb_id = tmdbData.imdb_id;
      cacheEntry.setExpiry(30); // scadenza 30 giorni

      await this.tmdbCacheRepository.save(cacheEntry);
      this.logger.debug(`cache salvata: ${cacheKey} (tmdb_id: ${tmdbData.id})`);
    } catch (error) {
      // gestisce conflitti chiave duplicata con update automatico
      if (error.code === '23505') {
        try {
          await this.tmdbCacheRepository.update(
            { cache_key: cacheKey },
            {
              tmdb_data: tmdbData,
              tmdb_id: tmdbData.id,
              title: tmdbData.title,
              year: tmdbData.release_date
                ? new Date(tmdbData.release_date).getFullYear()
                : undefined,
              imdb_id: tmdbData.imdb_id,
              updated_at: new Date(),
            },
          );
          this.logger.debug(`cache aggiornata: ${cacheKey}`);
        } catch (updateError) {
          this.logger.error(`errore aggiornamento cache: ${updateError.message}`);
        }
      } else {
        this.logger.error(`errore salvataggio cache: ${error.message}`);
      }
    }
  }

  /**
   * pulisce cache tmdb scaduta
   */
  async cleanExpiredTmdbCache(): Promise<number> {
    try {
      const result = await this.tmdbCacheRepository
        .createQueryBuilder()
        .delete()
        .where('expires_at < :now', { now: new Date() })
        .execute();

      const deleted = result.affected || 0;
      if (deleted > 0) {
        this.logger.log(`🧹 pulite ${deleted} entries cache scadute`);
      }

      return deleted;
    } catch (error) {
      this.logger.error(`errore pulizia cache: ${error.message}`);
      return 0;
    }
  }

  /**
   * statistiche complete cache tmdb
   */
  async getTmdbCacheStats(): Promise<any> {
    try {
      const [totalEntries, recentEntries, expiredEntries, topHits] = await Promise.all([
        this.tmdbCacheRepository.count(),
        this.tmdbCacheRepository
          .createQueryBuilder()
          .where('created_at >= :date', {
            date: new Date(Date.now() - 7 * 24 * 60 * 60 * 1000),
          })
          .getCount(),
        this.tmdbCacheRepository
          .createQueryBuilder()
          .where('expires_at <= :now', { now: new Date() })
          .getCount(),
        this.tmdbCacheRepository.find({
          order: { hit_count: 'DESC' },
          take: 10,
        }),
      ]);

      return {
        totalEntries,
        recentEntries,
        expiredEntries,
        topHits: topHits.map((entry) => ({
          title: entry.title,
          hits: entry.hit_count,
          lastAccessed: entry.last_accessed_at,
          tmdbId: entry.tmdb_id,
        })),
        cacheEfficiency:
          totalEntries > 0
            ? (((totalEntries - expiredEntries) / totalEntries) * 100).toFixed(1)
            : '0',
      };
    } catch (error) {
      this.logger.error(`errore statistiche cache: ${error.message}`);
      return { error: error.message, totalEntries: 0 };
    }
  }

  // GESTIONE FILM (con flag is_enriched)

  /**
   * salva singolo film in database
   * imposta automaticamente is_enriched se ha tmdb_id
   */
  async saveMovie(movie: Movie): Promise<MovieEntity> {
    try {
      const movieEntity = this.movieToEntity(movie);
      
      // imposta flag is_enriched se ha dati tmdb
      movieEntity.is_enriched = !!(movieEntity.tmdb_id && movieEntity.tmdb_id > 0);
      
      const saved = await this.movieRepository.save(movieEntity);

      this.logger.debug(
        `film salvato: ${saved.title} (arricchito: ${saved.is_enriched})`
      );
      return saved;
    } catch (error) {
      this.logger.error(`errore salvataggio film ${movie.title}: ${error.message}`);
      throw error;
    }
  }

  /**
   * Recupera multipli film tramite array di IDs
   * Utile per recuperare i film di un utente specifico
   */
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

  /**
   * salva multipli film in batch con chunk ottimizzati
   * usa transaction per garantire atomicità
   */
  async saveMovies(movies: Movie[]): Promise<MovieEntity[]> {
    try {
      this.logger.log(`📦 avvio salvataggio batch: ${movies.length} film`);

      if (movies.length === 0) {
        this.logger.warn('⚠️ nessun film da salvare');
        return [];
      }

      const entities = movies.map((movie) => {
        const entity = this.movieToEntity(movie);
        // imposta flag is_enriched automaticamente
        entity.is_enriched = !!(entity.tmdb_id && entity.tmdb_id > 0);
        return entity;
      });

      // transaction per atomicità
      const savedEntities = await this.movieRepository.manager.transaction(
        async (transactionalEntityManager) => {
          const savedResults: MovieEntity[] = [];
          const CHUNK_SIZE = 500;

          this.logger.log(
            `salvataggio in ${Math.ceil(entities.length / CHUNK_SIZE)} chunk da max ${CHUNK_SIZE} film`,
          );

          for (let i = 0; i < entities.length; i += CHUNK_SIZE) {
            const chunk = entities.slice(i, i + CHUNK_SIZE);
            const chunkNumber = Math.floor(i / CHUNK_SIZE) + 1;

            this.logger.log(
              `chunk ${chunkNumber}: ${chunk.length} film (${i + 1}-${i + chunk.length})`,
            );

            try {
              // upsert per evitare duplicati
              const saved = await transactionalEntityManager.save(MovieEntity, chunk, {
                chunk: CHUNK_SIZE,
              });

              savedResults.push(...saved);
              this.logger.log(
                `✅ chunk ${chunkNumber} salvato: ${saved.length} film (totale: ${savedResults.length})`,
              );
            } catch (chunkError) {
              this.logger.error(`❌ errore chunk ${chunkNumber}: ${chunkError.message}`);

              // fallback: salvataggio individuale
              this.logger.log(`🔄 fallback: salvataggio individuale chunk ${chunkNumber}`);

              for (const entity of chunk) {
                try {
                  const individual = await transactionalEntityManager.save(MovieEntity, entity);
                  savedResults.push(individual);
                  this.logger.debug(`✅ salvato individualmente: ${entity.title}`);
                } catch (individualError) {
                  this.logger.error(
                    `❌ fallito salvataggio ${entity.title}: ${individualError.message}`,
                  );
                }
              }
            }
          }

          return savedResults;
        },
      );

      const enrichedCount = savedEntities.filter((e) => e.is_enriched).length;

      this.logger.log(`=== BATCH COMPLETATO ===`);
      this.logger.log(`film richiesti: ${movies.length}`);
      this.logger.log(`film salvati: ${savedEntities.length}`);
      this.logger.log(`film arricchiti: ${enrichedCount}`);
      this.logger.log(`success rate: ${((savedEntities.length / movies.length) * 100).toFixed(1)}%`);

      return savedEntities;
    } catch (error) {
      this.logger.error(`❌ errore critico salvataggio batch: ${error.message}`);
      throw error;
    }
  }

  /**
   * recupera tutti i film dal database
   */
  async getAllMovies(): Promise<Movie[]> {
    try {
      const entities = await this.movieRepository.find({
        order: { title: 'ASC' },
      });

      const movies = entities.map((entity) => this.entityToMovie(entity));

      const enrichedCount = movies.filter((m) => m.tmdb_id).length;
      const watchedCount = movies.filter((m) => m.is_watched).length;

      this.logger.log(
        `📚 recuperati ${movies.length} film (${enrichedCount} arricchiti, ${watchedCount} visti)`,
      );

      return movies;
    } catch (error) {
      this.logger.error(`errore recupero film: ${error.message}`);
      return [];
    }
  }

  /**
   * cerca film per id
   */
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

  /**
   * cerca film per titolo e anno (con fallback case-insensitive)
  */
  async findMovieByTitleYear(title: string, year?: number): Promise<Movie | null> {
    try {
      const whereConditions: FindOptionsWhere<MovieEntity> = { title };
      if (year) whereConditions.year = year;

      let entity = await this.movieRepository.findOne({
        where: whereConditions,
      });

      // fallback: ricerca case-insensitive
      if (!entity && title) {
        entity = await this.movieRepository
          .createQueryBuilder('movie')
          .where('LOWER(movie.title) = LOWER(:title)', { title })
          .andWhere(year ? 'movie.year = :year' : '1=1', { year })
          .getOne();
      }

      if (entity) {
        this.logger.debug(
          `film trovato: ${title} (${year}) - arricchito: ${entity.is_enriched}`,
        );
        return this.entityToMovie(entity);
      }

      return null;
    } catch (error) {
      this.logger.error(`errore ricerca film ${title}: ${error.message}`);
      return null;
    }
  }

  /**
   * recupera solo film NON arricchiti
   * utile per enrichment selettivo
   */
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

  /**
   * elimina tutti i film dal database
   */
  async deleteAllMovies(): Promise<void> {
    try {
      const result = await this.movieRepository.delete({});
      this.logger.log(`🗑️ eliminati ${result.affected} film dal database`);
    } catch (error) {
      this.logger.error(`errore eliminazione film: ${error.message}`);
      throw error;
    }
  }

  /**
   * elimina film per sorgente specifica
   */
  async deleteMoviesBySource(source: DataSource): Promise<void> {
    try {
      const result = await this.movieRepository.delete({ source: source as any });
      this.logger.log(`🗑️ eliminati ${result.affected} film da ${source}`);
    } catch (error) {
      this.logger.error(`errore eliminazione film da ${source}: ${error.message}`);
      throw error;
    }
  }

  // STATISTICHE DATABASE

  /**
   * statistiche complete database con nuovo flag is_enriched
   */
  async getStats(): Promise<any> {
    try {
      const [
        totalMovies,
        watchedCount,
        enrichedCount,
        unenrichedCount,
        imdbMovies,
        letterboxdMovies,
        cacheStats,
      ] = await Promise.all([
        this.movieRepository.count(),
        this.movieRepository.count({ where: { is_watched: true } }),
        this.movieRepository.count({ where: { is_enriched: true } }),
        this.movieRepository.count({ where: { is_enriched: false } }),
        this.movieRepository.count({ where: { source: DataSource.IMDB as any } }),
        this.movieRepository.count({ where: { source: DataSource.LETTERBOXD as any } }),
        this.getTmdbCacheStats(),
      ]);

      const stats = {
        database: {
          totalMovies,
          enrichedMovies: enrichedCount,
          unenrichedMovies: unenrichedCount,
          watchedCount,
          watchlistCount: totalMovies - watchedCount,
          imdbCount: imdbMovies,
          letterboxdCount: letterboxdMovies,
          enrichmentRate:
            totalMovies > 0 ? ((enrichedCount / totalMovies) * 100).toFixed(1) : '0',
        },
        tmdbCache: cacheStats,
        system: {
          databaseType: 'postgresql',
          cacheEnabled: true,
          enrichmentOptimized: true,
          lastUpdated: new Date().toISOString(),
        },
      };

      this.logger.debug(`statistiche recuperate: ${totalMovies} film totali`);
      return stats;
    } catch (error) {
      this.logger.error(`errore recupero statistiche: ${error.message}`);
      return {
        error: error.message,
        database: { totalMovies: 0, enrichedMovies: 0 },
      };
    }
  }

  /**
   * health check database
   */
  async healthCheck(): Promise<{ status: string; details?: any }> {
    try {
      const stats = await this.getStats();
      const cacheStats = await this.getTmdbCacheStats();

      return {
        status: 'healthy',
        details: {
          database_type: 'postgresql',
          connection: 'active',
          movies: stats.database,
          tmdb_cache: cacheStats,
          enrichment_system: 'optimized with is_enriched flag',
          timestamp: new Date().toISOString(),
        },
      };
    } catch (error) {
      return {
        status: 'unhealthy',
        details: {
          error: error.message,
          database_type: 'postgresql',
          timestamp: new Date().toISOString(),
        },
      };
    }
  }

  // GESTIONE ENRICHMENT SESSIONS

  async setEnrichmentStatus(
    sessionId: string,
    status: Omit<EnrichmentStatus, 'sessionId'>,
  ): Promise<void> {
    try {
      this.enrichmentSessions.set(sessionId, { sessionId, ...status });
      this.logger.debug(
        `enrichment status aggiornato: ${sessionId} (${status.processed}/${status.total})`,
      );
    } catch (error) {
      this.logger.error(`errore aggiornamento status ${sessionId}: ${error.message}`);
    }
  }

  async getEnrichmentStatus(sessionId: string): Promise<EnrichmentStatus | null> {
    try {
      const status = this.enrichmentSessions.get(sessionId);
      if (status) {
        this.logger.debug(`enrichment status recuperato: ${sessionId}`);
      }
      return status || null;
    } catch (error) {
      this.logger.error(`errore recupero status ${sessionId}: ${error.message}`);
      return null;
    }
  }

  // GESTIONE ANALYTICS CACHE

  async saveAnalytics(userId: string, analyticsData: any): Promise<void> {
    try {
      const expiresAt = new Date(Date.now() + 24 * 60 * 60 * 1000); // 24h
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

      this.logger.log(`📊 analytics recuperate per utente: ${userId}`);
      return cached.data;
    } catch (error) {
      this.logger.error(`errore recupero analytics ${userId}: ${error.message}`);
      return null;
    }
  }

  /**
   * Update movie
   */
  async updateMovie(id: string, updates: Partial<Movie>): Promise<Movie> {
    try {
      const entity = await this.movieRepository.findOne({ where: { id } });
      
      if (!entity) {
        throw new Error(`Movie ${id} not found`);
      }

      // Aggiorna i campi
      Object.assign(entity, this.movieToEntity({ ...this.entityToMovie(entity), ...updates }));
      
      const updated = await this.movieRepository.save(entity);
      
      this.logger.debug(`✅ film ${id} aggiornato`);
      
      return this.entityToMovie(updated);
    } catch (error) {
      this.logger.error(`errore aggiornamento film ${id}: ${error.message}`);
      throw error;
    }
  }

  /**
   * Delete movie
   */
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

  /**
   * Get movie by TMDB ID
   */
  async getMovieByTmdbId(tmdbId: number): Promise<Movie | null> {
    try {
      const entity = await this.movieRepository.findOne({ 
        where: { tmdb_id: tmdbId } 
      });

      if (!entity) {
        this.logger.debug(`film non trovato per tmdb_id: ${tmdbId}`);
        return null;
      }

      return this.entityToMovie(entity);
    } catch (error) {
      this.logger.error(`errore recupero film tmdb_id ${tmdbId}: ${error.message}`);
      return null;
    }
  }

  /**
   * Save or update movie (upsert)
   */
  async saveOrUpdateMovie(movie: Movie): Promise<Movie> {
    try {
      // Cerca se esiste già
      let existing = await this.findMovieByTitleYear(movie.title, movie.year);

      if (existing) {
        // Aggiorna
        return await this.updateMovie(existing.id, movie);
      } else {
        // Crea nuovo
        const entity = await this.saveMovie(movie);
        return this.entityToMovie(entity);
      }
    } catch (error) {
      this.logger.error(`errore save/update film ${movie.title}: ${error.message}`);
      throw error;
    }
  }

  /**
   * Get cached TMDB data by title and year
   */
  async getCachedTmdbData(title: string, year?: number): Promise<any | null> {
    try {
      const cacheKey = `${title.toLowerCase()}_${year || 'unknown'}`;
      return await this.getTmdbCache(cacheKey);
    } catch (error) {
      this.logger.error(`errore recupero cache per ${title}: ${error.message}`);
      return null;
    }
  }

  /**
   * Associate movie with user (stub - da implementare con user-movies service)
   */
  async associateMovieWithUser(
    userId: string,
    movieId: string,
    isWatched: boolean,
  ): Promise<void> {
    this.logger.debug(
      `associazione film ${movieId} con utente ${userId} (watched: ${isWatched})`,
    );
    // Implementazione delegata a UserMoviesService
  }

  /**
   * Get user movies (stub - da implementare con user-movies service)
   */
  async getUserMovies(userId: string): Promise<Movie[]> {
    this.logger.debug(`recupero film per utente ${userId}`);
    // Implementazione delegata a UserMoviesService
    // Per ora ritorna tutti i film
    return await this.getAllMovies();
  }

  // UTILITY: CONVERSIONI ENTITY <-> MODEL

  /**
   * converte movie model in movieentity per database e imposta automaticamente is_enriched
   */
  private movieToEntity(movie: Movie): MovieEntity {
    const entity = new MovieEntity();

    entity.id = movie.id;
    entity.title = movie.title;
    entity.year = movie.year;
    entity.user_rating = movie.user_rating;
    entity.watched_date = movie.watched_date;
    entity.user_review = movie.user_review;
    entity.is_watched = movie.is_watched;
    entity.source = movie.source as any;

    entity.tmdb_id = movie.tmdb_id;
    entity.genres = movie.genres?.length > 0 ? movie.genres : undefined;
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
    entity.production_companies =
      movie.production_companies?.length > 0 ? movie.production_companies : undefined;
    entity.production_countries =
      movie.production_countries?.length > 0 ? movie.production_countries : undefined;
    entity.spoken_languages =
      movie.spoken_languages?.length > 0 ? movie.spoken_languages : undefined;
    entity.keywords = movie.keywords?.length > 0 ? movie.keywords : undefined;
    entity.certification = movie.certification;
    entity.trailer_url = movie.trailer_url;

    // imposta flag is_enriched se ha dati tmdb validi
    entity.is_enriched = !!(movie.tmdb_id && movie.tmdb_id > 0);

    return entity;
  }

  /**
   * converte movieentity in movie model per business logic
   */
  private entityToMovie(entity: MovieEntity): Movie {
    return {
      id: entity.id,
      title: entity.title,
      year: entity.year,
      user_rating: entity.user_rating,
      watched_date: entity.watched_date,
      user_review: entity.user_review,
      is_watched: entity.is_watched,
      source: entity.source as any,

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

  /**
   * verifica disponibilità database
   */
  isDatabaseAvailable(): boolean {
    return this.movieRepository !== undefined && this.tmdbCacheRepository !== undefined;
  }
}