import { Injectable, Logger } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { MovieEntity } from '../../database/entities/movie.entity';
import { UserEntity } from '../../database/entities/user.entity';
import { DatabaseService } from '../../database/database.service';
import { TmdbService } from '../tmdb/tmdb.service';
import { WebsocketGateway } from '../websocket/websocket.gateway';
import { Movie, BatchUploadResult, EnrichmentResult, FailedMovie } from '../../common/interfaces/movie.interface';
import { v4 as uuidv4 } from 'uuid';

@Injectable()
export class MoviesService {
  private readonly logger = new Logger(MoviesService.name);

  constructor(
    @InjectRepository(MovieEntity)
    private movieRepository: Repository<MovieEntity>,
    @InjectRepository(UserEntity)
    private userRepository: Repository<UserEntity>,
    private databaseService: DatabaseService,
    private tmdbService: TmdbService,
    private websocketGateway: WebsocketGateway,
  ) {}

  /**
   * 🔥 Batch upload OTTIMIZZATO con progress real-time
   * Supporta file grandi (1000+ film)
   */
  async batchUpload(
    watchlist: Movie[],
    watched: Movie[],
    userId: string,
  ): Promise<BatchUploadResult> {
    const sessionId = uuidv4();
    const totalMovies = watchlist.length + watched.length;

    this.logger.log(`🎬 BATCH UPLOAD avviato`);
    this.logger.log(`   Session: ${sessionId}`);
    this.logger.log(`   User: ${userId}`);
    this.logger.log(`   Watchlist: ${watchlist.length} film`);
    this.logger.log(`   Watched: ${watched.length} film`);
    this.logger.log(`   TOTALE: ${totalMovies} film`);

    try {
      // STEP 1: Salva tutti i film nel database (senza enrichment)
      this.logger.log(`💾 Salvataggio ${totalMovies} film in database...`);
      const allMovies = [...watchlist, ...watched];
      await this.databaseService.saveMovies(allMovies);
      this.logger.log(`✅ ${totalMovies} film salvati`);

      // STEP 2: Notifica inizio enrichment
      await this.websocketGateway.notifyEnrichmentStarted(sessionId, totalMovies);

      // STEP 3: Enrichment con progress real-time
      this.logger.log(`✨ Avvio enrichment TMDB...`);

      let processedCount = 0;

      // Enrichment watchlist
      const watchlistResult = await this.enrichMoviesWithProgress(
        watchlist,
        sessionId,
        processedCount,
        totalMovies,
      );
      processedCount += watchlist.length;

      // Enrichment watched
      const watchedResult = await this.enrichMoviesWithProgress(
        watched,
        sessionId,
        processedCount,
        totalMovies,
      );

      // STEP 4: Notifica completamento
      await this.websocketGateway.notifyEnrichmentCompleted(
        sessionId,
        totalMovies,
        totalMovies,
      );

      // Summary
      const summary = {
        totalMovies,
        watchlistCount: watchlist.length,
        watchedCount: watched.length,
        totalEnriched: watchlistResult.successfulMovies.length + watchedResult.successfulMovies.length,
        overallSuccessRate: ((watchlistResult.successfulMovies.length + watchedResult.successfulMovies.length) / totalMovies) * 100,
        cacheHitsTotal: 0,
      };

      this.logger.log(`✅ BATCH UPLOAD COMPLETATO`);
      this.logger.log(`   Enriched: ${summary.totalEnriched}/${totalMovies}`);
      this.logger.log(`   Success rate: ${summary.overallSuccessRate.toFixed(1)}%`);

      return {
        sessionId,
        watchlistResult,
        watchedResult,
        summary,
      };

    } catch (error) {
      this.logger.error(`❌ Errore batch upload: ${error.message}`);
      
      // Notifica errore via WebSocket
      await this.websocketGateway.notifyEnrichmentError(
        sessionId,
        error.message,
      );

      throw error;
    }
  }

  /**
   * 🔥 Enrichment con progress tracking real-time
   */
  private async enrichMoviesWithProgress(
    movies: Movie[],
    sessionId: string,
    startOffset: number,
    totalMovies: number,
  ): Promise<EnrichmentResult> {
    const successfulMovies: Movie[] = [];
    const failedMovies: FailedMovie[] = [];

    for (let i = 0; i < movies.length; i++) {
      const movie = movies[i];
      const currentProgress = startOffset + i + 1;

      try {
        // Enrichment singolo film
        const enrichedMovie = await this.tmdbService.enrichMovie(movie);
        
        if (enrichedMovie && enrichedMovie.tmdb_id) {
          successfulMovies.push(enrichedMovie);
          
          // Salva film enriched
          await this.databaseService.saveMovie(enrichedMovie);
        } else {
          failedMovies.push({
            movie,
            error: 'No TMDB data found',
          });
        }

        // 🔥 Invia progress via WebSocket
        await this.websocketGateway.notifyEnrichmentProgress(
          sessionId,
          currentProgress,
          totalMovies,
          movie.title,
        );

        // Piccola pausa per evitare rate limiting TMDB
        await this.sleep(300);

      } catch (error) {
        this.logger.warn(`⚠️ Errore enrichment "${movie.title}": ${error.message}`);
        failedMovies.push({
          movie,
          error: error.message,
        });
      }
    }

    const successRate = (successfulMovies.length / movies.length) * 100;

    return {
      sessionId: uuidv4(),
      successfulMovies,
      failedMovies,
      totalProcessed: movies.length,
      successRate,
      cacheHits: 0,
    };
  }

  /**
   * Get all movies for a user
   */
  async getAllMovies(userId: string): Promise<MovieEntity[]> {
    return this.movieRepository.find({
      where: { user: { id: userId } },
      order: { created_at: 'DESC' },
    });
  }

  /**
   * Delete all movies for a user
   */
  async deleteAllMovies(userId: string): Promise<{ message: string; deletedCount: number }> {
    const movies = await this.getAllMovies(userId);
    const deletedCount = movies.length;

    await this.movieRepository.remove(movies);

    this.logger.log(`🗑️ Eliminati ${deletedCount} film per user ${userId}`);

    return {
      message: 'All movies deleted successfully',
      deletedCount,
    };
  }

  /**
   * Utility: sleep
   */
  private sleep(ms: number): Promise<void> {
    return new Promise(resolve => setTimeout(resolve, ms));
  }
}