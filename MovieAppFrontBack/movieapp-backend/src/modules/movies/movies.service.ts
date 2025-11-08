//service principale gestione film con enrichment batch e websocket notifications

import { Injectable, Logger } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository, Not, IsNull } from 'typeorm';
import { MovieEntity } from '../../database/entities/movie.entity';
import { UserMovieEntity } from '../../database/entities/user-movie.entity';
import { TmdbService } from '../tmdb/tmdb.service';
import { DatabaseService } from '../../database/database.service';
import { WebsocketGateway } from '../websocket/websocket.gateway';
import { Movie } from '../../common/interfaces/movie.interface';
import { v4 as uuidv4 } from 'uuid';
import { UserMoviesService } from './user-movies.service';

//interfaccia film fallito durante enrichment
interface FailedMovie {
  movie: Movie;
  error: string;
}

//risultato operazione enrichment
interface EnrichmentResult {
  sessionId: string;
  successfulMovies: Movie[];
  failedMovies: FailedMovie[];
  totalProcessed: number;
  successRate: number;
  cacheHits: number;
}

//risultato batch upload
interface BatchResult {
  sessionId: string;
  watchlistResult: EnrichmentResult;
  watchedResult: EnrichmentResult;
  summary: {
    totalMovies: number;
    watchlistCount: number;
    watchedCount: number;
    totalEnriched: number;
    overallSuccessRate: number;
    cacheHitsTotal: number;
  };
  counters: {
    fromFile: {
      watched: number;
      watchlist: number;
      total: number;
    };
    afterRefresh: {
      totalWatched: number;
      totalWatchlist: number;
      total: number;
    };
  };
}

//risultato batch upload con associazione utente
export interface BatchUploadResult {
  sessionId: string;
  watchlistResult: EnrichmentResult;
  watchedResult: EnrichmentResult;
  summary: {
    totalMovies: number;
    watchlistCount: number;
    watchedCount: number;
    totalEnriched: number;
    overallSuccessRate: number;
    cacheHitsTotal: number;
  };
  importCounters: {
    watchedFromFile: number;
    watchlistFromFile: number;
    totalWatched: number;
    totalWatchlist: number;
  };
}

@Injectable()
export class MoviesService {
  private readonly logger = new Logger(MoviesService.name);

  constructor(
    @InjectRepository(MovieEntity)
    private readonly movieRepository: Repository<MovieEntity>,
    @InjectRepository(UserMovieEntity)
    private readonly userMovieRepository: Repository<UserMovieEntity>,
    private readonly tmdbService: TmdbService,
    private readonly databaseService: DatabaseService,
    private readonly websocketGateway: WebsocketGateway,
    private readonly userMoviesService: UserMoviesService,
  ) {}

  //verifica stato sistema
  async healthCheck(): Promise<{
    status: string;
    timestamp: string;
    database: string;
    moviesCount: number;
  }> {
    try {
      const count = await this.movieRepository.count();
      
      return {
        status: 'ok',
        timestamp: new Date().toISOString(),
        database: 'connected',
        moviesCount: count,
      };
    } catch (error) {
      this.logger.error(`health check fallito: ${error.message}`);
      return {
        status: 'error',
        timestamp: new Date().toISOString(),
        database: 'disconnected',
        moviesCount: 0,
      };
    }
  }

  //inizializza app al primo avvio
  async initializeApp(): Promise<{
    needsSync: boolean;
    message: string;
    stats?: any;
  }> {
    try {
      this.logger.log('verifica necessita inizializzazione database...');

      const movieCount = await this.movieRepository.count();
      const enrichedCount = await this.movieRepository.count({
        where: { is_enriched: true },
      });

      this.logger.log(`database: ${movieCount} film, ${enrichedCount} arricchiti`);

      //primo avvio: scarica 10k film popolari per autocomplete
      if (movieCount === 0) {
        this.logger.log('primo avvio: avvio sync 10.000 film popolari in background...');
        
        //avvia sync in background senza bloccare
        this.tmdbService.syncPopularMovies(10000)
          .then(result => {
            this.logger.log(`sync iniziale completato: ${result.synced} film`);
          })
          .catch(error => {
            this.logger.error(`errore sync iniziale: ${error.message}`);
          });
        
        return {
          needsSync: true,
          message: 'primo avvio: scaricamento 10k film popolari avviato in background',
          stats: { movieCount: 0, enrichedCount: 0 },
        };
      }

      const unenrichedCount = movieCount - enrichedCount;
      if (unenrichedCount > 0) {
        this.logger.log(`trovati ${unenrichedCount} film non arricchiti`);
        
        return {
          needsSync: true,
          message: `inizializzazione completata: ${enrichedCount}/${movieCount} film arricchiti`,
          stats: {
            movieCount,
            enrichedCount,
            unenrichedCount,
          },
        };
      }

      return {
        needsSync: false,
        message: 'database gia inizializzato',
        stats: { movieCount, enrichedCount },
      };
    } catch (error) {
      this.logger.error(`errore inizializzazione: ${error.message}`);
      throw error;
    }
  }

  //arricchisce lista film con dati tmdb usando cache
  async enrichMovies(movies: Movie[]): Promise<EnrichmentResult> {
    const sessionId = uuidv4();
    const successfulMovies: Movie[] = [];
    const failedMovies: FailedMovie[] = [];
    let cacheHits = 0;

    const totalMovies = movies.length;

    this.logger.log(`enrichment batch: ${totalMovies} film`);
    this.websocketGateway.notifyEnrichmentStarted(sessionId, totalMovies);

    for (let i = 0; i < movies.length; i++) {
      const movie = movies[i];

      try {
        //cerca film esistente usando id univoco del film non tmdb_id
        const existingMovie = await this.databaseService.findMovieById(movie.id);

        if (existingMovie?.is_enriched) {
          //film gia arricchito nel database
          this.logger.log(`cache hit: ${movie.title} (${movie.id})`);
          successfulMovies.push(existingMovie);
          cacheHits++;
        } else {
          //arricchisci film con tmdb
          const enrichedMovie = await this.tmdbService.enrichMovie(movie);

          if (enrichedMovie) {
            //salva film arricchito nel database
            await this.databaseService.saveMovie(enrichedMovie);
            
            successfulMovies.push(enrichedMovie);
          } else {
            failedMovies.push({
              movie,
              error: 'enrichment fallito',
            });
          }
        }

        this.websocketGateway.notifyEnrichmentProgress(
          sessionId,
          i + 1,
          totalMovies,
          movie.title,
        );
      } catch (error) {
        this.logger.error(
          `errore enrichment ${movie.title}: ${error.message}`,
        );
        failedMovies.push({
          movie,
          error: error.message,
        });
      }
    }

    this.websocketGateway.notifyEnrichmentCompleted(sessionId, totalMovies);

    const successRate =
      totalMovies > 0 
        ? (successfulMovies.length / totalMovies) * 100 
        : 0;

    this.logger.log(`enrichment completato:`);
    this.logger.log(`   successo: ${successfulMovies.length}/${totalMovies}`);
    this.logger.log(`   cache hits: ${cacheHits}`);
    this.logger.log(`   falliti: ${failedMovies.length}`);

    return {
      sessionId,
      successfulMovies,
      failedMovies,
      totalProcessed: totalMovies,
      successRate,
      cacheHits,
    };
  }

  //batch upload watchlist e watched
  async batchUpload(
    watchlist: Movie[],
    watched: Movie[],
    userId: string,
  ): Promise<BatchResult> {
    const sessionId = uuidv4();

    this.logger.log(`batch upload per user ${userId}:`);
    this.logger.log(`  watchlist: ${watchlist.length} film`);
    this.logger.log(`  watched: ${watched.length} film`);

    const watchlistResult = await this.enrichMovies(watchlist);
    const watchedResult = await this.enrichMovies(watched);

    const fromFile = {
      watched: watched.length,
      watchlist: watchlist.length,
      total: watched.length + watchlist.length,
    };

    await this.userMoviesService.associateMoviesToUser(
      userId,
      watchlistResult.successfulMovies,
      'watchlist',
    );

    await this.userMoviesService.associateMoviesToUser(
      userId,
      watchedResult.successfulMovies,
      'watched',
    );

    const stats = await this.userMoviesService.getUserMovieStats(userId);
    const afterRefresh = {
      totalWatched: stats.watched,
      totalWatchlist: stats.watchlist,
      total: stats.total,
    };

    const summary = {
      totalMovies: watchlist.length + watched.length,
      watchlistCount: watchlist.length,
      watchedCount: watched.length,
      totalEnriched:
        watchlistResult.successfulMovies.length +
        watchedResult.successfulMovies.length,
      overallSuccessRate:
        ((watchlistResult.successfulMovies.length +
          watchedResult.successfulMovies.length) /
          (watchlist.length + watched.length)) *
        100,
      cacheHitsTotal:
        watchlistResult.cacheHits + watchedResult.cacheHits,
    };

    return {
      sessionId,
      watchlistResult,
      watchedResult,
      summary,
      counters: {
        fromFile,
        afterRefresh,
      },
    };
  }

  //batch upload con associazione utente completa
  async batchUploadWithUserAssociation(
    userId: string,
    watchlist: Movie[],
    watched: Movie[],
  ): Promise<BatchUploadResult> {
    this.logger.log(`=== BATCH UPLOAD INIZIO ===`);
    this.logger.log(`user: ${userId}`);
    this.logger.log(`watchlist: ${watchlist.length} film`);
    this.logger.log(`watched: ${watched.length} film`);

    const watchedFromFile = watched.length;
    const watchlistFromFile = watchlist.length;

    //step 1: enrichment separato
    this.logger.log(`enrichment batch: ${watchlist.length} film watchlist`);
    const watchlistResult = await this.enrichMovies(watchlist);
    
    this.logger.log(`enrichment batch: ${watched.length} film watched`);
    const watchedResult = await this.enrichMovies(watched);

    //step 2: associa tutti film in batch
    this.logger.log(`=== ASSOCIAZIONE BATCH ===`);
    
    //associa watchlist come watchlist
    this.logger.log(`associazione ${watchlistResult.successfulMovies.length} film come WATCHLIST`);
    await this.userMoviesService.associateMoviesToUser(
      userId,
      watchlistResult.successfulMovies,
      'watchlist',
    );

    //associa watched come watched
    this.logger.log(`associazione ${watchedResult.successfulMovies.length} film come WATCHED`);
    await this.userMoviesService.associateMoviesToUser(
      userId,
      watchedResult.successfulMovies,
      'watched',
    );

    //step 3: recupera statistiche finali
    this.logger.log(`recupero statistiche finali...`);
    const stats = await this.userMoviesService.getUserMovieStats(userId);
    
    this.logger.log(`=== CONTATORI FINALI ===`);
    this.logger.log(`watched totali: ${stats.watched}`);
    this.logger.log(`watchlist totali: ${stats.watchlist}`);
    this.logger.log(`totale: ${stats.total}`);

    const summary = {
      totalMovies: watchlist.length + watched.length,
      watchlistCount: watchlist.length,
      watchedCount: watched.length,
      totalEnriched:
        watchlistResult.successfulMovies.length +
        watchedResult.successfulMovies.length,
      overallSuccessRate:
        ((watchlistResult.successfulMovies.length +
          watchedResult.successfulMovies.length) /
          (watchlist.length + watched.length)) *
        100,
      cacheHitsTotal: watchlistResult.cacheHits + watchedResult.cacheHits,
    };

    return {
      sessionId: uuidv4(),
      watchlistResult,
      watchedResult,
      summary,
      importCounters: {
        watchedFromFile,
        watchlistFromFile,
        totalWatched: stats.watched,
        totalWatchlist: stats.watchlist,
      },
    };
  }

  //recupera film utente con filtro status
  async getUserMovies(userId: string, status?: string): Promise<Movie[]> {
    try {
      this.logger.log(`recupero film per user ${userId} (status: ${status || 'all'})`);

      const queryBuilder = this.userMovieRepository
        .createQueryBuilder('um')
        .leftJoinAndSelect('um.movie', 'movie')
        .where('um.userId = :userId', { userId });

      if (status && ['watched', 'watchlist'].includes(status)) {
        queryBuilder.andWhere('um.status = :status', { status });
      }

      const userMovies = await queryBuilder.getMany();

      const movies = userMovies
        .filter(um => um.movie)
        .map(um => ({
          ...this.entityToMovie(um.movie),
          status: um.status,
          user_rating: um.userRating,
          watched_date: um.watchedDate,
          user_review: um.userReview,
          is_favorite: um.isFavorite,
        }));

      this.logger.log(`trovati ${movies.length} film`);
      return movies;
    } catch (error) {
      this.logger.error(`errore getallmovies: ${error.message}`);
      throw error;
    }
  }

  //cerca film per titolo
  async searchMovies(query: string): Promise<Movie[]> {
    try {
      const entities = await this.movieRepository
        .createQueryBuilder('movie')
        .where('LOWER(movie.title) LIKE LOWER(:query)', {
          query: `%${query}%`,
        })
        .orderBy('movie.title', 'ASC')
        .take(50)
        .getMany();

      return entities.map((entity) => this.entityToMovie(entity));
    } catch (error) {
      this.logger.error(`errore ricerca film: ${error.message}`);
      throw error;
    }
  }

  //recupera statistiche utente
  async getUserStats(userId: string) {
    try {
      return await this.userMoviesService.getUserMovieStats(userId);
    } catch (error) {
      this.logger.error(`errore recupero user stats: ${error.message}`);
      throw error;
    }
  }

  //recupera statistiche generali database
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
            ? (enrichedMovies / totalMovies) * 100
            : 0,
      };
    } catch (error) {
      this.logger.error(`errore recupero stats: ${error.message}`);
      throw error;
    }
  }

  //converte entity database in model movie
  private entityToMovie(entity: MovieEntity): Movie {
    return {
      id: entity.id,
      title: entity.title,
      year: entity.year,
      source: entity.source,
      tmdb_id: entity.tmdb_id,
      is_enriched: entity.is_enriched,
      genres: entity.genres,
      director: entity.director,
      actors: entity.actors,
      overview: entity.overview,
      tagline: entity.tagline,
      runtime: entity.runtime,
      poster_url: entity.poster_url,
      backdrop_url: entity.backdrop_url,
      tmdb_rating: entity.tmdb_rating,
      vote_count: entity.vote_count,
      popularity: entity.popularity,
      budget: entity.budget,
      revenue: entity.revenue,
      status: entity.status,
      production_companies: entity.production_companies,
      production_countries: entity.production_countries,
      original_language: entity.original_language,
      original_title: entity.original_title,
      spoken_languages: entity.spoken_languages,
      adult: entity.adult,
      homepage: entity.homepage,
      imdb_id: entity.imdb_id,
      keywords: entity.keywords,
      certification: entity.certification,
      trailer_url: entity.trailer_url,
      created_at: entity.created_at,
      updated_at: entity.updated_at,
    };
  }
}