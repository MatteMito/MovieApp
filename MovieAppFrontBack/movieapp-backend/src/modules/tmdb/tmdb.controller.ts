// controller per ricerca film tmdb e sync con endpoint autocomplete

import {
  Controller,
  Get,
  Post,
  Body,
  Query,
  HttpStatus,
  HttpException,
  Logger,
} from '@nestjs/common';
import { TmdbService } from './tmdb.service';
import { DatabaseService } from '../../database/database.service';

// response wrapper standardizzato
interface ApiResponse<T = any> {
  success: boolean;
  data?: T;
  message?: string;
  timestamp: string;
}

@Controller('api/v1/tmdb')
export class TmdbController {
  private readonly logger = new Logger(TmdbController.name);

  constructor(
    private readonly tmdbService: TmdbService,
    private readonly databaseService: DatabaseService,
  ) {}

  // GET /api/v1/tmdb/autocomplete
  // ricerca film nel database locale per autocomplete
  @Get('autocomplete')
  async autocompleteMovies(
    @Query('query') query: string,
    @Query('limit') limit?: string,
  ): Promise<ApiResponse> {
    try {
      if (!query || query.trim().length === 0) {
        return {
          success: true,
          data: [],
          message: 'query vuota',
          timestamp: new Date().toISOString(),
        };
      }

      const parsedLimit = limit ? parseInt(limit, 10) : 10;

      this.logger.log(`autocomplete: "${query}" (limit: ${parsedLimit})`);

      // cerca nel db locale con ranking per popolarità
      const movies = await this.tmdbService.searchForAutocomplete(query, parsedLimit);

      this.logger.log(`trovati ${movies.length} film`);

      return {
        success: true,
        data: movies,
        message: `trovati ${movies.length} film`,
        timestamp: new Date().toISOString(),
      };
    } catch (error) {
      this.logger.error(`errore autocomplete: ${error.message}`);
      throw new HttpException(
        { 
          success: false, 
          message: error.message, 
          timestamp: new Date().toISOString() 
        },
        HttpStatus.INTERNAL_SERVER_ERROR,
      );
    }
  }

  // POST /api/v1/tmdb/sync-popular
  // sincronizza top 10k film popolari da tmdb nel database locale
  @Post('sync-popular')
  async syncPopularMovies(
    @Body() body: { limit?: number },
  ): Promise<ApiResponse> {
    try {
      const limit = body.limit || 10000; // default 10k film

      this.logger.log(`avvio sync ${limit} film popolari...`);

      // scarica e salva film popolari da tmdb
      const result = await this.tmdbService.syncPopularMovies(limit);

      return {
        success: true,
        data: result,
        message: `sincronizzati ${result.synced} film (${result.errors} errori)`,
        timestamp: new Date().toISOString(),
      };
    } catch (error) {
      this.logger.error(`errore sync: ${error.message}`);
      throw new HttpException(
        { success: false, message: error.message, timestamp: new Date().toISOString() },
        HttpStatus.INTERNAL_SERVER_ERROR,
      );
    }
  }

  // GET /api/v1/tmdb/stats
  // statistiche database: totali, arricchiti, percentuali
  @Get('stats')
  async getStats(): Promise<ApiResponse> {
    try {
      const stats = await this.databaseService.getSyncStats();

      return {
        success: true,
        data: stats,
        message: 'statistiche database',
        timestamp: new Date().toISOString(),
      };
    } catch (error) {
      this.logger.error(`errore stats: ${error.message}`);
      throw new HttpException(
        { success: false, message: error.message, timestamp: new Date().toISOString() },
        HttpStatus.INTERNAL_SERVER_ERROR,
      );
    }
  }
}