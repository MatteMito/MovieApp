// controller api rest per gestione film

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
} from '@nestjs/common';
import { MoviesService } from './movies.service';
import { Movie } from '../../common/interfaces/movie.interface';

// response wrapper standardizzato
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

  // HEALTH & STATUS

  /**
   * GET /api/v1/movies/health
   * health check endpoint
   */
  @Get('health')
  async healthCheck(): Promise<ApiResponse> {
    try {
      this.logger.log('🏥 health check richiesto');

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
   * GET /api/v1/movies/cache/stats
   * statistiche cache e database
   */
  @Get('cache/stats')
  async getCacheStats(): Promise<ApiResponse> {
    try {
      this.logger.log('📊 statistiche cache richieste');

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

  // ENRICHMENT ENDPOINTS

  /**
   * POST /api/v1/movies/enrich
   * arricchisce film con dati tmdb
   * usa cache intelligente e flag is_enriched
   */
  @Post('enrich')
  async enrichMovies(@Body() body: { movies: Movie[] }): Promise<ApiResponse> {
    try {
      this.logger.log(`🎬 richiesta enrichment per ${body.movies.length} film`);

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
        `✅ enrichment completato: ${enrichedCount}/${body.movies.length} film`,
      );

      return {
        success: true,
        data: result,
        message: `enrichment completato: ${enrichedCount}/${body.movies.length} film arricchiti`,
        timestamp: new Date().toISOString(),
      };
    } catch (error) {
      this.logger.error(`❌ errore enrichment: ${error.message}`);

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
   * POST /api/v1/movies/batch
   * batch upload watchlist + watched con auto-enrichment
   */
  @Post('batch')
  async batchUpload(
    @Body() body: { watchlist: Movie[]; watched: Movie[] },
  ): Promise<ApiResponse> {
    try {
      this.logger.log(
        `📦 batch upload: ${body.watchlist.length} watchlist + ${body.watched.length} watched`,
      );

      const result = await this.moviesService.batchUpload(
        body.watchlist,
        body.watched,
      );

      this.logger.log(
        `✅ batch upload completato: ${result.summary.totalEnriched} film arricchiti`,
      );

      return {
        success: true,
        data: result,
        message: `batch completato: ${result.summary.totalMovies} film processati`,
        timestamp: new Date().toISOString(),
      };
    } catch (error) {
      this.logger.error(`❌ errore batch upload: ${error.message}`);

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

  // GESTIONE FILM

  /**
   * GET /api/v1/movies/all
   * recupera tutti i film dal database
   */
  @Get('all')
  async getAllMovies(): Promise<ApiResponse> {
    try {
      this.logger.log('📚 richiesta tutti i film');

      const movies = await this.moviesService.getAllMovies();

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
   * GET /api/v1/movies/:id
   * recupera singolo film per id
   */
  @Get(':id')
  async getMovieById(@Param('id') id: string): Promise<ApiResponse> {
    try {
      this.logger.log(`🎬 richiesta film: ${id}`);

      const movie = await this.moviesService.getMovieById(id);

      if (!movie) {
        throw new HttpException(
          {
            success: false,
            message: 'film non trovato',
            timestamp: new Date().toISOString(),
          },
          HttpStatus.NOT_FOUND,
        );
      }

      return {
        success: true,
        data: movie,
        message: 'film recuperato',
        timestamp: new Date().toISOString(),
      };
    } catch (error) {
      this.logger.error(`errore recupero film ${id}: ${error.message}`);

      if (error instanceof HttpException) {
        throw error;
      }

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
   * GET /api/v1/movies/filter/unenriched
   * recupera solo film non arricchiti
   */
  @Get('filter/unenriched')
  async getUnenrichedMovies(): Promise<ApiResponse> {
    try {
      this.logger.log('📊 richiesta film non arricchiti');

      const movies = await this.moviesService.getUnenrichedMovies();

      return {
        success: true,
        data: { movies },
        message: `trovati ${movies.length} film da arricchire`,
        timestamp: new Date().toISOString(),
      };
    } catch (error) {
      this.logger.error(`errore recupero film non arricchiti: ${error.message}`);

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
   * DELETE /api/v1/movies/all
   * elimina tutti i film
   */
  @Delete('all')
  async deleteAllMovies(): Promise<ApiResponse> {
    try {
      this.logger.log('🗑️ richiesta eliminazione tutti i film');

      await this.moviesService.deleteAllMovies();

      return {
        success: true,
        message: 'tutti i film eliminati',
        timestamp: new Date().toISOString(),
      };
    } catch (error) {
      this.logger.error(`errore eliminazione film: ${error.message}`);

      throw new HttpException(
        {
          success: false,
          message: 'errore eliminazione film',
          timestamp: new Date().toISOString(),
        },
        HttpStatus.INTERNAL_SERVER_ERROR,
      );
    }
  }

  // RICERCA

  /**
   * GET /api/v1/movies/search
   * ricerca film con filtri multipli
   */
  @Get('search')
  async searchMovies(
    @Query('q') query?: string,
    @Query('genre') genre?: string,
    @Query('year') year?: string,
    @Query('director') director?: string,
    @Query('minRating') minRating?: string,
    @Query('maxRating') maxRating?: string,
    @Query('watched') watched?: string,
    @Query('sortBy') sortBy?: string,
    @Query('sortOrder') sortOrder?: 'ASC' | 'DESC',
    @Query('limit') limit?: string,
    @Query('offset') offset?: string,
  ): Promise<ApiResponse> {
    try {
      this.logger.log(`🔍 ricerca film: query="${query}"`);

      const filters = {
        query,
        genre,
        year: year ? parseInt(year) : undefined,
        director,
        minRating: minRating ? parseFloat(minRating) : undefined,
        maxRating: maxRating ? parseFloat(maxRating) : undefined,
        watched: watched !== undefined ? watched === 'true' : undefined,
        sortBy: sortBy || 'title',
        sortOrder: sortOrder || 'ASC',
        limit: limit ? parseInt(limit) : 50,
        offset: offset ? parseInt(offset) : 0,
      };

      const result = await this.moviesService.searchMovies(filters);

      return {
        success: true,
        data: {
          movies: result.movies,
          total: result.total,
          filters,
        },
        message: `trovati ${result.total} film`,
        timestamp: new Date().toISOString(),
      };
    } catch (error) {
      this.logger.error(`errore ricerca: ${error.message}`);

      throw new HttpException(
        {
          success: false,
          message: 'errore ricerca film',
          timestamp: new Date().toISOString(),
        },
        HttpStatus.INTERNAL_SERVER_ERROR,
      );
    }
  }

  /**
   * GET /api/v1/movies/initialize
   * inizializza app client con dati completi e ritorna film e statistiche per bootstrap veloce
   */
  @Get('initialize')
  async initializeApp(): Promise<ApiResponse> {
    try {
      this.logger.log('🚀 inizializzazione app client');

      const [movies, stats] = await Promise.all([
        this.moviesService.getAllMovies(),
        this.moviesService.getStats(),
      ]);

      const enrichedCount = movies.filter((m) => m.tmdb_id).length;

      this.logger.log(
        `✅ inizializzazione completata: ${movies.length} film (${enrichedCount} arricchiti)`,
      );

      return {
        success: true,
        data: {
          movies,
          stats: stats.database,
        },
        message: `app inizializzata con ${movies.length} film`,
        timestamp: new Date().toISOString(),
      };
    } catch (error) {
      this.logger.error(`errore inizializzazione: ${error.message}`);

      throw new HttpException(
        {
          success: false,
          message: 'errore inizializzazione app',
          timestamp: new Date().toISOString(),
        },
        HttpStatus.INTERNAL_SERVER_ERROR,
      );
    }
  }
}