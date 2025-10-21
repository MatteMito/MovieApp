//controller per gestione liste film

import {
  Controller,
  Get,
  Post,
  Body,
  Query,
  HttpException,
  HttpStatus,
  Logger,
} from '@nestjs/common';
import { ListsService, ListFilters } from './lists.service';

@Controller('api/v1/lists')
export class ListsController {
  private readonly logger = new Logger(ListsController.name);

  constructor(private readonly listsService: ListsService) {}

  /**
   * POST /api/v1/lists/custom
   * crea lista personalizzata con filtri
   */
  @Post('custom')
  async createCustomList(
    @Body() body: { name: string; description?: string; filters: ListFilters },
  ) {
    try {
      this.logger.log(`richiesta lista custom: ${body.name}`);

      const list = await this.listsService.createCustomList(
        body.name,
        body.filters,
        body.description,
      );

      return {
        success: true,
        data: list,
        message: `lista "${body.name}" creata con ${list.totalMovies} film`,
        timestamp: new Date().toISOString(),
      };
    } catch (error) {
      this.logger.error(`errore lista custom: ${error.message}`);

      throw new HttpException(
        {
          success: false,
          message: 'errore creazione lista',
          timestamp: new Date().toISOString(),
        },
        HttpStatus.INTERNAL_SERVER_ERROR,
      );
    }
  }

  /**
   * GET /api/v1/lists/top-rated
   * top film per rating
   */
  @Get('top-rated')
  async getTopRated(@Query('limit') limit?: string) {
    try {
      const limitNum = limit ? parseInt(limit) : 50;
      this.logger.log(`richiesta top rated (limit: ${limitNum})`);

      const list = await this.listsService.getTopRatedMovies(limitNum);

      return {
        success: true,
        data: list,
        message: `top ${list.totalMovies} film recuperati`,
        timestamp: new Date().toISOString(),
      };
    } catch (error) {
      this.logger.error(`errore top rated: ${error.message}`);

      throw new HttpException(
        {
          success: false,
          message: 'errore recupero top rated',
          timestamp: new Date().toISOString(),
        },
        HttpStatus.INTERNAL_SERVER_ERROR,
      );
    }
  }

  /**
   * GET /api/v1/lists/recent
   * film recenti (ultimi 5 anni)
   */
  @Get('recent')
  async getRecent(@Query('limit') limit?: string) {
    try {
      const limitNum = limit ? parseInt(limit) : 50;
      this.logger.log(`richiesta recent movies (limit: ${limitNum})`);

      const list = await this.listsService.getRecentMovies(limitNum);

      return {
        success: true,
        data: list,
        message: `${list.totalMovies} film recenti recuperati`,
        timestamp: new Date().toISOString(),
      };
    } catch (error) {
      this.logger.error(`errore recent movies: ${error.message}`);

      throw new HttpException(
        {
          success: false,
          message: 'errore recupero film recenti',
          timestamp: new Date().toISOString(),
        },
        HttpStatus.INTERNAL_SERVER_ERROR,
      );
    }
  }

  /**
   * GET /api/v1/lists/classics
   * film classici (1950-1999)
   */
  @Get('classics')
  async getClassics() {
    try {
      this.logger.log('richiesta classic movies');

      const list = await this.listsService.getClassicMovies();

      return {
        success: true,
        data: list,
        message: `${list.totalMovies} film classici recuperati`,
        timestamp: new Date().toISOString(),
      };
    } catch (error) {
      this.logger.error(`errore classic movies: ${error.message}`);

      throw new HttpException(
        {
          success: false,
          message: 'errore recupero film classici',
          timestamp: new Date().toISOString(),
        },
        HttpStatus.INTERNAL_SERVER_ERROR,
      );
    }
  }

  /**
   * GET /api/v1/lists/long
   * film lunghi (180+ min)
   */
  @Get('long')
  async getLong(@Query('minRuntime') minRuntime?: string) {
    try {
      const minRuntimeNum = minRuntime ? parseInt(minRuntime) : 180;
      this.logger.log(`richiesta long movies (${minRuntimeNum}+ min)`);

      const list = await this.listsService.getLongMovies(minRuntimeNum);

      return {
        success: true,
        data: list,
        message: `${list.totalMovies} film lunghi recuperati`,
        timestamp: new Date().toISOString(),
      };
    } catch (error) {
      this.logger.error(`errore long movies: ${error.message}`);

      throw new HttpException(
        {
          success: false,
          message: 'errore recupero film lunghi',
          timestamp: new Date().toISOString(),
        },
        HttpStatus.INTERNAL_SERVER_ERROR,
      );
    }
  }

  /**
   * GET /api/v1/lists/decade/:decade
   * film per decade
   */
  @Get('decade/:decade')
  async getByDecade(@Query('decade') decade: string) {
    try {
      const decadeNum = parseInt(decade);
      this.logger.log(`richiesta movies decade ${decadeNum}`);

      const list = await this.listsService.getMoviesByDecade(decadeNum);

      return {
        success: true,
        data: list,
        message: `${list.totalMovies} film anni ${decadeNum} recuperati`,
        timestamp: new Date().toISOString(),
      };
    } catch (error) {
      this.logger.error(`errore decade movies: ${error.message}`);

      throw new HttpException(
        {
          success: false,
          message: 'errore recupero film per decade',
          timestamp: new Date().toISOString(),
        },
        HttpStatus.INTERNAL_SERVER_ERROR,
      );
    }
  }

  /**
   * GET /api/v1/lists/watchlist
   * watchlist non vista
   */
  @Get('watchlist')
  async getWatchlist() {
    try {
      this.logger.log('richiesta unwatched watchlist');

      const list = await this.listsService.getUnwatchedWatchlist();

      return {
        success: true,
        data: list,
        message: `${list.totalMovies} film da vedere`,
        timestamp: new Date().toISOString(),
      };
    } catch (error) {
      this.logger.error(`errore watchlist: ${error.message}`);

      throw new HttpException(
        {
          success: false,
          message: 'errore recupero watchlist',
          timestamp: new Date().toISOString(),
        },
        HttpStatus.INTERNAL_SERVER_ERROR,
      );
    }
  }

  /**
   * GET /api/v1/lists/genre/:genre
   * film per genere
   */
  @Get('genre/:genre')
  async getByGenre(@Query('genre') genre: string) {
    try {
      this.logger.log(`richiesta movies genere: ${genre}`);

      const list = await this.listsService.getMoviesByGenre(genre);

      return {
        success: true,
        data: list,
        message: `${list.totalMovies} film ${genre} recuperati`,
        timestamp: new Date().toISOString(),
      };
    } catch (error) {
      this.logger.error(`errore genre movies: ${error.message}`);

      throw new HttpException(
        {
          success: false,
          message: 'errore recupero film per genere',
          timestamp: new Date().toISOString(),
        },
        HttpStatus.INTERNAL_SERVER_ERROR,
      );
    }
  }

  /**
   * GET /api/v1/lists/director/:director
   * film per regista
   */
  @Get('director/:director')
  async getByDirector(@Query('director') director: string) {
    try {
      this.logger.log(`richiesta movies regista: ${director}`);

      const list = await this.listsService.getMoviesByDirector(director);

      return {
        success: true,
        data: list,
        message: `${list.totalMovies} film di ${director} recuperati`,
        timestamp: new Date().toISOString(),
      };
    } catch (error) {
      this.logger.error(`errore director movies: ${error.message}`);

      throw new HttpException(
        {
          success: false,
          message: 'errore recupero film per regista',
          timestamp: new Date().toISOString(),
        },
        HttpStatus.INTERNAL_SERVER_ERROR,
      );
    }
  }

  /**
   * GET /api/v1/lists/genres
   * tutti i generi disponibili
   */
  @Get('genres')
  async getAllGenres() {
    try {
      this.logger.log('richiesta tutti i generi');

      const genres = await this.listsService.getAllGenres();

      return {
        success: true,
        data: { genres },
        message: `${genres.length} generi recuperati`,
        timestamp: new Date().toISOString(),
      };
    } catch (error) {
      this.logger.error(`errore generi: ${error.message}`);

      throw new HttpException(
        {
          success: false,
          message: 'errore recupero generi',
          timestamp: new Date().toISOString(),
        },
        HttpStatus.INTERNAL_SERVER_ERROR,
      );
    }
  }

  /**
   * GET /api/v1/lists/directors
   * tutti i registi disponibili
   */
  @Get('directors')
  async getAllDirectors() {
    try {
      this.logger.log('richiesta tutti i registi');

      const directors = await this.listsService.getAllDirectors();

      return {
        success: true,
        data: { directors },
        message: `${directors.length} registi recuperati`,
        timestamp: new Date().toISOString(),
      };
    } catch (error) {
      this.logger.error(`errore registi: ${error.message}`);

      throw new HttpException(
        {
          success: false,
          message: 'errore recupero registi',
          timestamp: new Date().toISOString(),
        },
        HttpStatus.INTERNAL_SERVER_ERROR,
      );
    }
  }
}