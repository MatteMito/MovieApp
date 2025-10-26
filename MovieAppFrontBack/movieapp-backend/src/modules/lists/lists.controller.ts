// FILE: src/modules/lists/lists.controller.ts
// Controller completo per gestione liste personalizzate

import {
  Controller,
  Get,
  Post,
  Put,
  Delete,
  Body,
  Param,
  Query,
  HttpStatus,
  HttpException,
  Logger,
} from '@nestjs/common';
import { ListsService } from './lists.service';
import { CreateListDto, UpdateListDto, AddMovieToListDto } from '../../common/dto/list.dto';

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
   * POST /api/v1/lists
   * Crea nuova lista personalizzata
   */
  @Post()
  async createList(@Body() createDto: CreateListDto): Promise<ApiResponse> {
    try {
      this.logger.log(`📝 Creazione lista: ${createDto.name}`);

      const list = await this.listsService.createUserList(createDto);

      return {
        success: true,
        data: list,
        message: 'Lista creata con successo',
        timestamp: new Date().toISOString(),
      };
    } catch (error) {
      this.logger.error(`❌ Errore creazione lista: ${error.message}`);
      throw new HttpException(
        { success: false, message: error.message, timestamp: new Date().toISOString() },
        HttpStatus.INTERNAL_SERVER_ERROR,
      );
    }
  }

  /**
   * GET /api/v1/lists?userId=xxx
   * Recupera tutte le liste dell'utente
   */
  @Get()
  async getUserLists(@Query('userId') userId: string): Promise<ApiResponse> {
    try {
      if (!userId) {
        throw new HttpException(
          { success: false, message: 'userId mancante', timestamp: new Date().toISOString() },
          HttpStatus.BAD_REQUEST,
        );
      }

      this.logger.log(`📋 Recupero liste per utente ${userId}`);

      const lists = await this.listsService.getUserLists(userId);

      return {
        success: true,
        data: lists,
        message: `${lists.length} liste trovate`,
        timestamp: new Date().toISOString(),
      };
    } catch (error) {
      this.logger.error(`❌ Errore recupero liste: ${error.message}`);
      throw new HttpException(
        { success: false, message: error.message, timestamp: new Date().toISOString() },
        HttpStatus.INTERNAL_SERVER_ERROR,
      );
    }
  }

  /**
   * GET /api/v1/lists/public?limit=20&userId=xxx
   * Recupera liste pubbliche (escluse quelle dell'utente)
   */
  @Get('public')
  async getPublicLists(
    @Query('limit') limit?: string,
    @Query('userId') userId?: string,  // 🔥 AGGIUNTO
  ): Promise<ApiResponse> {
    try {
      const limitNum = limit ? parseInt(limit, 10) : 20;
      this.logger.log(`🌐 Recupero liste pubbliche (limit: ${limitNum}, exclude: ${userId || 'nessuno'})`);

      const lists = await this.listsService.getPublicLists(limitNum, userId);  // 🔥 PASSA userId

      return {
        success: true,
        data: lists,
        message: `${lists.length} liste pubbliche`,
        timestamp: new Date().toISOString(),
      };
    } catch (error) {
      this.logger.error(`❌ Errore liste pubbliche: ${error.message}`);
      throw new HttpException(
        { success: false, message: error.message, timestamp: new Date().toISOString() },
        HttpStatus.INTERNAL_SERVER_ERROR,
      );
    }
  }

  /**
   * GET /api/v1/lists/:id
   * Dettaglio singola lista con film
   */
  @Get(':id')
  async getListById(@Param('id') listId: string): Promise<ApiResponse> {
    try {
      this.logger.log(`📄 Recupero dettaglio lista ${listId}`);

      const list = await this.listsService.getListWithMovies(listId);

      if (!list) {
        throw new HttpException(
          { success: false, message: 'Lista non trovata', timestamp: new Date().toISOString() },
          HttpStatus.NOT_FOUND,
        );
      }

      return {
        success: true,
        data: list,
        message: 'Lista trovata',
        timestamp: new Date().toISOString(),
      };
    } catch (error) {
      this.logger.error(`❌ Errore recupero lista: ${error.message}`);
      throw new HttpException(
        { success: false, message: error.message, timestamp: new Date().toISOString() },
        error.status || HttpStatus.INTERNAL_SERVER_ERROR,
      );
    }
  }

  /**
   * PUT /api/v1/lists/:id
   * Aggiorna lista esistente
   */
  @Put(':id')
  async updateList(
    @Param('id') listId: string,
    @Body() updateDto: UpdateListDto,
  ): Promise<ApiResponse> {
    try {
      this.logger.log(`✏️ Aggiornamento lista ${listId}`);

      const list = await this.listsService.updateList(listId, updateDto);

      return {
        success: true,
        data: list,
        message: 'Lista aggiornata',
        timestamp: new Date().toISOString(),
      };
    } catch (error) {
      this.logger.error(`❌ Errore aggiornamento lista: ${error.message}`);
      throw new HttpException(
        { success: false, message: error.message, timestamp: new Date().toISOString() },
        HttpStatus.INTERNAL_SERVER_ERROR,
      );
    }
  }

  /**
   * DELETE /api/v1/lists/:id
   * Elimina lista
   */
  @Delete(':id')
  async deleteList(@Param('id') listId: string): Promise<ApiResponse> {
    try {
      this.logger.log(`🗑️ Eliminazione lista ${listId}`);

      await this.listsService.deleteList(listId);

      return {
        success: true,
        message: 'Lista eliminata',
        timestamp: new Date().toISOString(),
      };
    } catch (error) {
      this.logger.error(`❌ Errore eliminazione lista: ${error.message}`);
      throw new HttpException(
        { success: false, message: error.message, timestamp: new Date().toISOString() },
        HttpStatus.INTERNAL_SERVER_ERROR,
      );
    }
  }

  /**
   * POST /api/v1/lists/:id/movies
   * Aggiungi film a lista
   */
  @Post(':id/movies')
  async addMovieToList(
    @Param('id') listId: string,
    @Body() addMovieDto: AddMovieToListDto,
  ): Promise<ApiResponse> {
    try {
      this.logger.log(`➕ Aggiunta film ${addMovieDto.movie_id} a lista ${listId}`);

      const list = await this.listsService.addMovieToList(listId, addMovieDto.movie_id);

      return {
        success: true,
        data: list,
        message: 'Film aggiunto alla lista',
        timestamp: new Date().toISOString(),
      };
    } catch (error) {
      this.logger.error(`❌ Errore aggiunta film: ${error.message}`);
      throw new HttpException(
        { success: false, message: error.message, timestamp: new Date().toISOString() },
        HttpStatus.INTERNAL_SERVER_ERROR,
      );
    }
  }

  /**
   * DELETE /api/v1/lists/:id/movies/:movieId
   * Rimuovi film da lista
   */
  @Delete(':id/movies/:movieId')
  async removeMovieFromList(
    @Param('id') listId: string,
    @Param('movieId') movieId: string,
  ): Promise<ApiResponse> {
    try {
      this.logger.log(`➖ Rimozione film ${movieId} da lista ${listId}`);

      const list = await this.listsService.removeMovieFromList(listId, movieId);

      return {
        success: true,
        data: list,
        message: 'Film rimosso dalla lista',
        timestamp: new Date().toISOString(),
      };
    } catch (error) {
      this.logger.error(`❌ Errore rimozione film: ${error.message}`);
      throw new HttpException(
        { success: false, message: error.message, timestamp: new Date().toISOString() },
        HttpStatus.INTERNAL_SERVER_ERROR,
      );
    }
  }

  /**
   * POST /api/v1/lists/:id/follow
   * Segui lista pubblica
   */
  @Post(':id/follow')
  async followList(
    @Param('id') listId: string,
    @Body('userId') userId: string,
  ): Promise<ApiResponse> {
    try {
      if (!userId) {
        throw new HttpException(
          { success: false, message: 'userId mancante', timestamp: new Date().toISOString() },
          HttpStatus.BAD_REQUEST,
        );
      }

      this.logger.log(`👥 Utente ${userId} segue lista ${listId}`);

      const list = await this.listsService.followList(listId, userId);

      return {
        success: true,
        data: list,
        message: 'Lista seguita con successo',
        timestamp: new Date().toISOString(),
      };
    } catch (error) {
      this.logger.error(`❌ Errore follow lista: ${error.message}`);
      throw new HttpException(
        { success: false, message: error.message, timestamp: new Date().toISOString() },
        HttpStatus.INTERNAL_SERVER_ERROR,
      );
    }
  }
}