// controller con endpoint per gestione liste personalizzate

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
import { ListsNotificationsService } from './lists-notifications.service';
import { CreateListDto, UpdateListDto, AddMovieToListDto } from '../../common/dto/list.dto';

@Controller('api/v1/lists')
export class ListsController {
  constructor(
    private readonly listsService: ListsService,
    private readonly notificationsService: ListsNotificationsService,
  ) {}

  // ===== ENDPOINT PUBBLICI (SENZA AUTH) =====

  // GET /api/v1/lists/public
  // ottieni tutte le liste pubbliche con filtri opzionali
  @Get('public')
  async getPublicLists(
    @Query('search') search?: string, // ricerca testuale nel nome/descrizione
    @Query('sortBy') sortBy?: string, // ordinamento (es: followers, created_at)
    @Query('limit') limit?: string, // numero massimo risultati
    @Query('userId') userId?: string, // filtra per utente specifico
  ) {
    const parsedLimit = limit ? parseInt(limit, 10) : 20;
    return this.listsService.getPublicLists({ search, sortBy });
  }

  // ===== ENDPOINT AUTENTICATI =====

  // GET /api/v1/lists/my
  // ottieni tutte le liste dell'utente (pubbliche e private)
  @Get('my')
  async getMyLists(@Query('userId') userId: string) {
    if (!userId) {
      throw new HttpException('userId richiesto', HttpStatus.BAD_REQUEST);
    }
    return this.listsService.getUserLists(userId);
  }

  // GET /api/v1/lists/:id
  // ottieni dettagli lista specifica con array di film
  @Get(':id')
  async getListById(@Param('id') id: string, @Query('userId') userId?: string) {
    return this.listsService.getListById(id, userId);
  }

  // POST /api/v1/lists
  // crea nuova lista per l'utente
  @Post()
  async createList(@Body() createListDto: CreateListDto) {
    const userId = createListDto.user_id;
    if (!userId) {
      throw new HttpException('user_id richiesto', HttpStatus.BAD_REQUEST);
    }
    return this.listsService.createList(userId, createListDto);
  }

  // PUT /api/v1/lists/:id
  // aggiorna nome, descrizione, visibilità di una lista
  @Put(':id')
  async updateList(
    @Param('id') id: string,
    @Body() updateListDto: UpdateListDto,
    @Query('userId') userId?: string,
  ) {
    if (!userId) {
      throw new HttpException('userId richiesto', HttpStatus.BAD_REQUEST);
    }
    return this.listsService.updateList(id, userId, updateListDto);
  }

  // DELETE /api/v1/lists/:id
  // elimina lista (solo se proprietario)
  @Delete(':id')
  async deleteList(@Param('id') id: string, @Query('userId') userId?: string) {
    if (!userId) {
      throw new HttpException('userId richiesto', HttpStatus.BAD_REQUEST);
    }
    return this.listsService.deleteList(id, userId);
  }

  // ===== GESTIONE FILM NELLE LISTE =====

  // POST /api/v1/lists/:id/movies
  // aggiungi film a lista esistente
  @Post(':id/movies')
  async addMovieToList(
    @Param('id') listId: string,
    @Body() addMovieDto: AddMovieToListDto,
    @Query('userId') userId?: string,
  ) {
    if (!userId) {
      throw new HttpException('userId richiesto', HttpStatus.BAD_REQUEST);
    }
    return this.listsService.addMovieToList(listId, userId, addMovieDto.movie_id);
  }

  // DELETE /api/v1/lists/:id/movies/:movieId
  // rimuovi film da lista
  @Delete(':id/movies/:movieId')
  async removeMovieFromList(
    @Param('id') listId: string,
    @Param('movieId') movieId: string,
    @Query('userId') userId?: string,
  ) {
    if (!userId) {
      throw new HttpException('userId richiesto', HttpStatus.BAD_REQUEST);
    }
    return this.listsService.removeMovieFromList(listId, userId, movieId);
  }

  // ===== SOCIAL FEATURES =====

  // POST /api/v1/lists/:id/follow
  // segui una lista pubblica di un altro utente
  @Post(':id/follow')
  async followList(
    @Param('id') listId: string,
    @Body() body: { userId: string },
  ) {
    const userId = body.userId;
    if (!userId) {
      throw new HttpException('userId richiesto', HttpStatus.BAD_REQUEST);
    }
    return this.listsService.followList(listId, userId);
  }

  // DELETE /api/v1/lists/:id/follow
  // smetti di seguire una lista
  @Delete(':id/follow')
  async unfollowList(
    @Param('id') listId: string,
    @Query('userId') userId?: string,
  ) {
    if (!userId) {
      throw new HttpException('userId richiesto', HttpStatus.BAD_REQUEST);
    }
    return this.listsService.unfollowList(listId, userId);
  }

  // GET /api/v1/lists/:id/followers
  // ottieni lista di utenti che seguono questa lista
  @Get(':id/followers')
  async getListFollowers(@Param('id') listId: string) {
    return this.listsService.getListFollowers(listId);
  }

  // POST /api/v1/lists/:id/copy
  // duplica una lista pubblica nella propria collezione
  @Post(':id/copy')
  async copyList(
    @Param('id') listId: string,
    @Body() body: { userId: string; newName?: string },
  ) {
    const userId = body.userId;
    if (!userId) {
      throw new HttpException('userId richiesto', HttpStatus.BAD_REQUEST);
    }
    return this.listsService.copyList(listId, userId, body.newName);
  }

  // ===== NOTIFICHE INTELLIGENTI =====

  // GET /api/v1/lists/notifications/check
  // endpoint per testare manualmente controllo notifiche
  @Get('notifications/check')
  async checkNotifications() {
    return this.notificationsService.triggerNotificationsManually();
  }
}