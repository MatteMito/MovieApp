// File: src/modules/lists/lists.controller.ts
// AGGIORNATO: tutti i metodi richiedono userId

import {
  Controller,
  Get,
  Query,
  HttpStatus,
  HttpException,
  Logger,
} from '@nestjs/common';
import { ListsService } from './lists.service';

interface ApiResponse<T = any> {
  success: boolean;
  data?: T;
  message?: string;
  timestamp: string;
}

@Controller('api/v1/lists')
export class ListsController {
  private readonly logger = new Logger(ListsController.name);

  constructor(private readonly listsService: ListsService) {}

  /**
   * GET /api/v1/lists/top-rated?userId=xxx&limit=20
   */
  @Get('top-rated')
  async getTopRatedMovies(
    @Query('userId') userId: string,
    @Query('limit') limit?: string,
  ): Promise<ApiResponse> {
    try {
      if (!userId) {
        throw new HttpException(
          { success: false, message: 'userId mancante', timestamp: new Date().toISOString() },
          HttpStatus.BAD_REQUEST,
        );
      }

      const limitNum = limit ? parseInt(limit, 10) : 20;
      this.logger.log(`🎬 top rated per utente ${userId}`);

      const list = await this.listsService.createCustomList(
        'Top Rated',
        {
          userId,
          minRating: 7,
          sortBy: 'rating',
          sortOrder: 'DESC',
        },
        'Film con i voti più alti',
      );

      // Limita i risultati
      list.movies = list.movies.slice(0, limitNum);
      list.totalMovies = list.movies.length;

      return {
        success: true,
        data: list,
        message: `top ${limitNum} film`,
        timestamp: new Date().toISOString(),
      };
    } catch (error) {
      this.logger.error(`errore top rated: ${error.message}`);
      throw new HttpException(
        { success: false, message: error.message, timestamp: new Date().toISOString() },
        HttpStatus.INTERNAL_SERVER_ERROR,
      );
    }
  }

  /**
   * GET /api/v1/lists/recent?userId=xxx&limit=20
   */
  @Get('recent')
  async getRecentMovies(
    @Query('userId') userId: string,
    @Query('limit') limit?: string,
  ): Promise<ApiResponse> {
    try {
      if (!userId) {
        throw new HttpException(
          { success: false, message: 'userId mancante', timestamp: new Date().toISOString() },
          HttpStatus.BAD_REQUEST,
        );
      }

      const limitNum = limit ? parseInt(limit, 10) : 20;
      this.logger.log(`🎬 film recenti per utente ${userId}`);

      const list = await this.listsService.createCustomList(
        'Recent Movies',
        {
          userId,
          sortBy: 'year',
          sortOrder: 'DESC',
        },
        'Film più recenti',
      );

      list.movies = list.movies.slice(0, limitNum);
      list.totalMovies = list.movies.length;

      return {
        success: true,
        data: list,
        message: `${limitNum} film recenti`,
        timestamp: new Date().toISOString(),
      };
    } catch (error) {
      this.logger.error(`errore recent movies: ${error.message}`);
      throw new HttpException(
        { success: false, message: error.message, timestamp: new Date().toISOString() },
        HttpStatus.INTERNAL_SERVER_ERROR,
      );
    }
  }

  /**
   * GET /api/v1/lists/classics?userId=xxx
   */
  @Get('classics')
  async getClassicMovies(@Query('userId') userId: string): Promise<ApiResponse> {
    try {
      if (!userId) {
        throw new HttpException(
          { success: false, message: 'userId mancante', timestamp: new Date().toISOString() },
          HttpStatus.BAD_REQUEST,
        );
      }

      this.logger.log(`🎬 classici per utente ${userId}`);

      const list = await this.listsService.createCustomList(
        'Classics',
        {
          userId,
          maxYear: 1980,
          minRating: 7,
          sortBy: 'year',
          sortOrder: 'ASC',
        },
        'Film classici (pre-1980)',
      );

      return {
        success: true,
        data: list,
        message: 'film classici',
        timestamp: new Date().toISOString(),
      };
    } catch (error) {
      this.logger.error(`errore classics: ${error.message}`);
      throw new HttpException(
        { success: false, message: error.message, timestamp: new Date().toISOString() },
        HttpStatus.INTERNAL_SERVER_ERROR,
      );
    }
  }

  /**
   * GET /api/v1/lists/long?userId=xxx&minRuntime=150
   */
  @Get('long')
  async getLongMovies(
    @Query('userId') userId: string,
    @Query('minRuntime') minRuntime?: string,
  ): Promise<ApiResponse> {
    try {
      if (!userId) {
        throw new HttpException(
          { success: false, message: 'userId mancante', timestamp: new Date().toISOString() },
          HttpStatus.BAD_REQUEST,
        );
      }

      const minRuntimeNum = minRuntime ? parseInt(minRuntime, 10) : 150;
      this.logger.log(`🎬 film lunghi per utente ${userId}`);

      const list = await this.listsService.createCustomList(
        'Long Movies',
        {
          userId,
          sortBy: 'runtime',
          sortOrder: 'DESC',
        },
        `Film con durata >= ${minRuntimeNum} minuti`,
      );

      // Filtra per runtime manualmente
      list.movies = list.movies.filter(m => (m.runtime || 0) >= minRuntimeNum);
      list.totalMovies = list.movies.length;

      return {
        success: true,
        data: list,
        message: 'film lunghi',
        timestamp: new Date().toISOString(),
      };
    } catch (error) {
      this.logger.error(`errore long movies: ${error.message}`);
      throw new HttpException(
        { success: false, message: error.message, timestamp: new Date().toISOString() },
        HttpStatus.INTERNAL_SERVER_ERROR,
      );
    }
  }

  /**
   * GET /api/v1/lists/by-genre?userId=xxx&genre=Action
   */
  @Get('by-genre')
  async getMoviesByGenre(
    @Query('userId') userId: string,
    @Query('genre') genre?: string,
  ): Promise<ApiResponse> {
    try {
      if (!userId) {
        throw new HttpException(
          { success: false, message: 'userId mancante', timestamp: new Date().toISOString() },
          HttpStatus.BAD_REQUEST,
        );
      }

      if (!genre) {
        throw new HttpException(
          { success: false, message: 'genre mancante', timestamp: new Date().toISOString() },
          HttpStatus.BAD_REQUEST,
        );
      }

      this.logger.log(`🎬 film per genere ${genre} per utente ${userId}`);

      const list = await this.listsService.createCustomList(
        `${genre} Movies`,
        {
          userId,
          genre,
          sortBy: 'rating',
          sortOrder: 'DESC',
        },
        `Film del genere ${genre}`,
      );

      return {
        success: true,
        data: list,
        message: `film genere ${genre}`,
        timestamp: new Date().toISOString(),
      };
    } catch (error) {
      this.logger.error(`errore by genre: ${error.message}`);
      throw new HttpException(
        { success: false, message: error.message, timestamp: new Date().toISOString() },
        HttpStatus.INTERNAL_SERVER_ERROR,
      );
    }
  }

  /**
   * GET /api/v1/lists/by-director?userId=xxx&director=Nolan
   */
  @Get('by-director')
  async getMoviesByDirector(
    @Query('userId') userId: string,
    @Query('director') director?: string,
  ): Promise<ApiResponse> {
    try {
      if (!userId) {
        throw new HttpException(
          { success: false, message: 'userId mancante', timestamp: new Date().toISOString() },
          HttpStatus.BAD_REQUEST,
        );
      }

      if (!director) {
        throw new HttpException(
          { success: false, message: 'director mancante', timestamp: new Date().toISOString() },
          HttpStatus.BAD_REQUEST,
        );
      }

      this.logger.log(`🎬 film per regista ${director} per utente ${userId}`);

      const list = await this.listsService.createCustomList(
        `${director} Films`,
        {
          userId,
          director,
          sortBy: 'year',
          sortOrder: 'DESC',
        },
        `Film diretti da ${director}`,
      );

      return {
        success: true,
        data: list,
        message: `film di ${director}`,
        timestamp: new Date().toISOString(),
      };
    } catch (error) {
      this.logger.error(`errore by director: ${error.message}`);
      throw new HttpException(
        { success: false, message: error.message, timestamp: new Date().toISOString() },
        HttpStatus.INTERNAL_SERVER_ERROR,
      );
    }
  }

  /**
   * GET /api/v1/lists/preset?userId=xxx
   * Ritorna le liste predefinite (top rated, recent, longest)
   */
  @Get('preset')
  async getPresetLists(@Query('userId') userId: string): Promise<ApiResponse> {
    try {
      if (!userId) {
        throw new HttpException(
          { success: false, message: 'userId mancante', timestamp: new Date().toISOString() },
          HttpStatus.BAD_REQUEST,
        );
      }

      this.logger.log(`🎬 preset lists per utente ${userId}`);

      const presetLists = await this.listsService.getPresetLists(userId);

      return {
        success: true,
        data: presetLists,
        message: 'preset lists',
        timestamp: new Date().toISOString(),
      };
    } catch (error) {
      this.logger.error(`errore preset lists: ${error.message}`);
      throw new HttpException(
        { success: false, message: error.message, timestamp: new Date().toISOString() },
        HttpStatus.INTERNAL_SERVER_ERROR,
      );
    }
  }
}