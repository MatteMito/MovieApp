//file: src/modules/movies/movies.controller.ts
//controller api rest per gestione film con endpoint initialize

import {
  Controller,
  Get,
  Post,
  Delete,
  Body,
  Param,
  Query,
  HttpStatus,
  HttpException,
  Logger,
  Headers,
} from '@nestjs/common';
import { MoviesService } from './movies.service';
import { Movie } from '../../common/interfaces/movie.interface';

//response wrapper standardizzato
interface ApiResponse<T = any> {
  success: boolean;
  data?: T;
  message?: string;
  timestamp: string;
  debug?: any;
}

@Controller('api/v1/movies')
export class MoviesController {
  private readonly logger = new Logger(MoviesController.name);

  constructor(private readonly moviesService: MoviesService) {}

  //health & status

  /**
   * get /api/v1/movies/health
   * health check endpoint
   */
  @Get('health')
  async healthCheck(): Promise<ApiResponse> {
    try {
      this.logger.log('health check richiesto');

      const health = await this.moviesService.healthCheck();

      return {
        success: true,
        data: health,
        message: 'sistema operativo',
        timestamp: new Date().toISOString(),
      };
    } catch (error) {
      this.logger.error(`errore health check: ${error.message}`);

      return {
        success: false,
        message: 'sistema degradato',
        timestamp: new Date().toISOString(),
        debug: { error: error.message },
      };
    }
  }

  /**
   * get /api/v1/movies/cache/stats
   * statistiche cache e database
   */
  @Get('cache/stats')
  async getCacheStats(): Promise<ApiResponse> {
    try {
      this.logger.log('statistiche cache richieste');

      const stats = await this.moviesService.getStats();

      return {
        success: true,
        data: stats,
        message: 'statistiche recuperate',
        timestamp: new Date().toISOString(),
      };
    } catch (error) {
      this.logger.error(`errore statistiche: ${error.message}`);

      throw new HttpException(
        {
          success: false,
          message: 'errore recupero statistiche',
          timestamp: new Date().toISOString(),
        },
        HttpStatus.INTERNAL_SERVER_ERROR,
      );
    }
  }

  /**
   * get /api/v1/movies/initialize
   * inizializza app al primo avvio: controlla se database vuoto e carica film popolari
   */
  @Get('initialize')
  async initializeApp(@Headers('x-user-id') userId?: string): Promise<ApiResponse> {
    try {
      this.logger.log('richiesta inizializzazione app');

      const result = await this.moviesService.initializeApp();

      return {
        success: true,
        data: result,
        message: result.needsSync 
          ? 'inizializzazione avviata in background'
          : 'database gia inizializzato',
        timestamp: new Date().toISOString(),
      };
    } catch (error) {
      this.logger.error(`errore inizializzazione: ${error.message}`);

      throw new HttpException(
        {
          success: false,
          message: `errore inizializzazione: ${error.message}`,
          timestamp: new Date().toISOString(),
        },
        HttpStatus.INTERNAL_SERVER_ERROR,
      );
    }
  }

  //enrichment endpoints

  /**
   * post /api/v1/movies/enrich
   * arricchisce film con dati tmdb
   * usa cache intelligente e flag is_enriched
   */
  @Post('enrich')
  async enrichMovies(@Body() body: { movies: Movie[] }): Promise<ApiResponse> {
    try {
      this.logger.log(`richiesta enrichment per ${body.movies.length} film`);

      if (!body.movies || body.movies.length === 0) {
        throw new HttpException(
          {
            success: false,
            message: 'nessun film fornito per enrichment',
            timestamp: new Date().toISOString(),
          },
          HttpStatus.BAD_REQUEST,
        );
      }

      const result = await this.moviesService.enrichMovies(body.movies);

      const enrichedCount = result.successfulMovies.filter((m) => m.tmdb_id).length;

      this.logger.log(
        `enrichment completato: ${enrichedCount}/${body.movies.length} film`,
      );

      return {
        success: true,
        data: result,
        message: `enrichment completato: ${enrichedCount}/${body.movies.length} film arricchiti`,
        timestamp: new Date().toISOString(),
      };
    } catch (error) {
      this.logger.error(`errore enrichment: ${error.message}`);

      throw new HttpException(
        {
          success: false,
          message: `errore enrichment: ${error.message}`,
          timestamp: new Date().toISOString(),
        },
        HttpStatus.INTERNAL_SERVER_ERROR,
      );
    }
  }

  /**
   * post /api/v1/movies/batch
   * batch upload watchlist + watched con auto-enrichment e associazione utente
   */
  @Post('batch')
  async batchUpload(
    @Body() body: { watchlist: Movie[]; watched: Movie[]; userId: string },
    @Headers('user-id') headerUserId?: string,
  ): Promise<ApiResponse> {
    try {
      //prendi userid dal body o dall'header
      const userId = body.userId || headerUserId;

      if (!userId) {
        throw new HttpException(
          {
            success: false,
            message: 'userId mancante. fornire userId nel body o nell\'header user-id',
            timestamp: new Date().toISOString(),
          },
          HttpStatus.BAD_REQUEST,
        );
      }

      this.logger.log(
        `batch upload per utente ${userId}: ${body.watchlist.length} watchlist + ${body.watched.length} watched`,
      );

      const result = await this.moviesService.batchUploadWithUserAssociation(
        userId,
        body.watchlist,
        body.watched,
      );

      this.logger.log(
        `batch upload completato: ${result.summary.totalEnriched} film arricchiti`,
      );
      
      this.logger.log(
        `contatori: ${result.importCounters.watchedFromFile} watched e ${result.importCounters.watchlistFromFile} watchlist nel file`,
      );

      return {
        success: true,
        data: {
          ...result,
          counters: {
            fromFile: {
              watched: result.importCounters.watchedFromFile,
              watchlist: result.importCounters.watchlistFromFile,
              total: result.importCounters.watchedFromFile + result.importCounters.watchlistFromFile,
            },
            afterRefresh: {
              watched: result.importCounters.totalWatched,
              watchlist: result.importCounters.totalWatchlist,
              total: result.importCounters.totalWatched + result.importCounters.totalWatchlist,
            },
          },
        },
        message: `batch completato: ${result.summary.totalMovies} film processati per utente ${userId}`,
        timestamp: new Date().toISOString(),
      };
    } catch (error) {
      this.logger.error(`errore batch upload: ${error.message}`);

      throw new HttpException(
        {
          success: false,
          message: `errore batch upload: ${error.message}`,
          timestamp: new Date().toISOString(),
        },
        HttpStatus.INTERNAL_SERVER_ERROR,
      );
    }
  }

  //gestione film

  /**
   * get /api/v1/movies/user/:userId
   * recupera tutti i film di un utente specifico
   */
  @Get('user/:userId')
  async getUserMovies(
    @Param('userId') userId: string,
    @Query('status') status?: string,
  ): Promise<ApiResponse> {
    try {
      this.logger.log(`richiesta film per user ${userId} (status: ${status || 'all'})`);

      const movies = await this.moviesService.getAllMovies(userId, status);

      return {
        success: true,
        data: { movies },
        message: `recuperati ${movies.length} film`,
        timestamp: new Date().toISOString(),
      };
    } catch (error) {
      this.logger.error(`errore recupero film: ${error.message}`);

      throw new HttpException(
        {
          success: false,
          message: 'errore recupero film',
          timestamp: new Date().toISOString(),
        },
        HttpStatus.INTERNAL_SERVER_ERROR,
      );
    }
  }

  /**
   * get /api/v1/movies/user/:userId/stats
   * statistiche film utente
   */
  @Get('user/:userId/stats')
  async getUserStats(@Param('userId') userId: string): Promise<ApiResponse> {
    try {
      this.logger.log(`richiesta statistiche per user ${userId}`);

      const stats = await this.moviesService.getUserStats(userId);

      return {
        success: true,
        data: stats,
        message: 'statistiche recuperate',
        timestamp: new Date().toISOString(),
      };
    } catch (error) {
      this.logger.error(`errore statistiche utente: ${error.message}`);

      throw new HttpException(
        {
          success: false,
          message: 'errore recupero statistiche',
          timestamp: new Date().toISOString(),
        },
        HttpStatus.INTERNAL_SERVER_ERROR,
      );
    }
  }

  /**
   * get /api/v1/movies/all
   * deprecato - usare /user/:userId
   */
  @Get('all')
  async getAllMovies(@Headers('x-user-id') userId?: string): Promise<ApiResponse> {
    this.logger.warn('endpoint /all deprecato. usare /user/:userId');
    
    try {
      if (!userId) {
        throw new HttpException(
          {
            success: false,
            message: 'userId richiesto negli headers (x-user-id)',
            timestamp: new Date().toISOString(),
          },
          HttpStatus.BAD_REQUEST,
        );
      }

      this.logger.log('richiesta tutti i film (deprecato)');

      const movies = await this.moviesService.getAllMovies(userId);

      return {
        success: true,
        data: { movies },
        message: `recuperati ${movies.length} film - attenzione: endpoint deprecato, usare /user/:userId`,
        timestamp: new Date().toISOString(),
      };
    } catch (error) {
      this.logger.error(`errore recupero film: ${error.message}`);

      throw new HttpException(
        {
          success: false,
          message: 'errore recupero film',
          timestamp: new Date().toISOString(),
        },
        HttpStatus.INTERNAL_SERVER_ERROR,
      );
    }
  }
}