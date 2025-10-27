import {
  Controller,
  Get,
  Post,
  Put,
  Delete,
  Body,
  Param,
  Query,
  UseGuards,
  Request,
  HttpException,
  HttpStatus,
} from '@nestjs/common';
import { JwtAuthGuard } from '../../common/guards/jwt-auth.guard';
import { ListsService } from './lists.service';
import { CreateListDto, UpdateListDto, AddMovieToListDto } from '../../common/dto/list.dto';

@Controller('api/v1/lists')
@UseGuards(JwtAuthGuard)
export class ListsController {
  constructor(private readonly listsService: ListsService) {}

  // ===== GESTIONE LISTE =====

  /**
   * GET /api/v1/lists/my
   * Ottieni tutte le liste dell'utente autenticato
   */
  @Get('my')
  async getMyLists(@Request() req) {
    const userId = req.user?.userId;
    if (!userId) {
      throw new HttpException('User ID non trovato', HttpStatus.UNAUTHORIZED);
    }
    return this.listsService.getUserLists(userId);
  }

  /**
   * GET /api/v1/lists/public
   * Ottieni tutte le liste pubbliche (con filtri opzionali)
   */
  @Get('public')
  async getPublicLists(
    @Query('search') search?: string,
    @Query('sortBy') sortBy?: string,
  ) {
    return this.listsService.getPublicLists({ search, sortBy });
  }

  /**
   * GET /api/v1/lists/:id
   * Ottieni dettagli lista specifica (con film)
   */
  @Get(':id')
  async getListById(@Param('id') id: string, @Request() req) {
    const userId = req.user?.userId;
    return this.listsService.getListById(id, userId);
  }

  /**
   * POST /api/v1/lists
   * Crea nuova lista
   */
  @Post()
  async createList(@Body() createListDto: CreateListDto, @Request() req) {
    const userId = req.user?.userId;
    if (!userId) {
      throw new HttpException('User ID non trovato', HttpStatus.UNAUTHORIZED);
    }
    return this.listsService.createList(userId, createListDto);
  }

  /**
   * PUT /api/v1/lists/:id
   * Aggiorna lista esistente
   */
  @Put(':id')
  async updateList(
    @Param('id') id: string,
    @Body() updateListDto: UpdateListDto,
    @Request() req,
  ) {
    const userId = req.user?.userId;
    if (!userId) {
      throw new HttpException('User ID non trovato', HttpStatus.UNAUTHORIZED);
    }
    return this.listsService.updateList(id, userId, updateListDto);
  }

  /**
   * DELETE /api/v1/lists/:id
   * Elimina lista
   */
  @Delete(':id')
  async deleteList(@Param('id') id: string, @Request() req) {
    const userId = req.user?.userId;
    if (!userId) {
      throw new HttpException('User ID non trovato', HttpStatus.UNAUTHORIZED);
    }
    return this.listsService.deleteList(id, userId);
  }

  // ===== GESTIONE FILM NELLE LISTE =====

  /**
   * POST /api/v1/lists/:id/movies
   * Aggiungi film a lista
   */
  @Post(':id/movies')
  async addMovieToList(
    @Param('id') listId: string,
    @Body() addMovieDto: AddMovieToListDto,
    @Request() req,
  ) {
    const userId = req.user?.userId;
    if (!userId) {
      throw new HttpException('User ID non trovato', HttpStatus.UNAUTHORIZED);
    }
    return this.listsService.addMovieToList(listId, userId, addMovieDto.movie_id);
  }

  /**
   * DELETE /api/v1/lists/:id/movies/:movieId
   * Rimuovi film da lista
   */
  @Delete(':id/movies/:movieId')
  async removeMovieFromList(
    @Param('id') listId: string,
    @Param('movieId') movieId: string,
    @Request() req,
  ) {
    const userId = req.user?.userId;
    if (!userId) {
      throw new HttpException('User ID non trovato', HttpStatus.UNAUTHORIZED);
    }
    return this.listsService.removeMovieFromList(listId, userId, movieId);
  }

  // ===== SOCIAL FEATURES =====

  /**
   * POST /api/v1/lists/:id/follow
   * Segui una lista pubblica
   */
  @Post(':id/follow')
  async followList(@Param('id') listId: string, @Request() req) {
    const userId = req.user?.userId;
    if (!userId) {
      throw new HttpException('User ID non trovato', HttpStatus.UNAUTHORIZED);
    }
    return this.listsService.followList(listId, userId);
  }

  /**
   * DELETE /api/v1/lists/:id/follow
   * Smetti di seguire una lista
   */
  @Delete(':id/follow')
  async unfollowList(@Param('id') listId: string, @Request() req) {
    const userId = req.user?.userId;
    if (!userId) {
      throw new HttpException('User ID non trovato', HttpStatus.UNAUTHORIZED);
    }
    return this.listsService.unfollowList(listId, userId);
  }

  /**
   * GET /api/v1/lists/:id/followers
   * Ottieni followers di una lista
   */
  @Get(':id/followers')
  async getListFollowers(@Param('id') listId: string) {
    return this.listsService.getListFollowers(listId);
  }
}