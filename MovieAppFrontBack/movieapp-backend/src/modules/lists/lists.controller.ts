// controller per gestione liste condivise
// endpoint per crud liste, gestione film, follow/unfollow, copia

import {
  Controller,
  Get,
  Post,
  Put,
  Delete,
  Body,
  Param,
  Query,
  HttpException,
  HttpStatus,
} from '@nestjs/common';
import { ListsService } from './lists.service';
import { CreateListDto, UpdateListDto, AddMovieToListDto } from '../../common/dto/list.dto';

@Controller('api/v1/lists')
export class ListsController {
  constructor(private readonly listsService: ListsService) {}

  // ===== endpoint pubblici (senza autenticazione) =====

  /**
   * endpoint: GET /api/v1/lists/public
   * ottieni tutte le liste pubbliche
   * accessibile senza autenticazione
   */
  @Get('public')
  async getPublicLists(
    @Query('search') search?: string,
    @Query('sortBy') sortBy?: string,
    @Query('limit') limit?: string,
    @Query('userId') userId?: string,
  ) {
    const parsedLimit = limit ? parseInt(limit, 10) : 20;
    return this.listsService.getPublicLists({ search, sortBy });
  }

  // ===== endpoint autenticati =====

  /**
   * endpoint: GET /api/v1/lists/my
   * ottieni tutte le liste dell'utente autenticato
   */
  @Get('my')
  async getMyLists(@Query('userId') userId: string) {
    if (!userId) {
      throw new HttpException('userId richiesto', HttpStatus.BAD_REQUEST);
    }
    return this.listsService.getUserLists(userId);
  }

  /**
   * endpoint: GET /api/v1/lists/:id
   * ottieni dettagli lista specifica con film popolati
   */
  @Get(':id')
  async getListById(@Param('id') id: string, @Query('userId') userId?: string) {
    return this.listsService.getListById(id, userId);
  }

  /**
   * endpoint: POST /api/v1/lists
   * crea nuova lista
   * body: { user_id, name, description?, is_public?, movie_ids? }
   */
  @Post()
  async createList(@Body() createListDto: CreateListDto) {
    const userId = createListDto.user_id;
    if (!userId) {
      throw new HttpException('user_id richiesto', HttpStatus.BAD_REQUEST);
    }
    return this.listsService.createList(userId, createListDto);
  }

  /**
   * endpoint: PUT /api/v1/lists/:id
   * aggiorna lista esistente (solo proprietario)
   * body: { name?, description?, is_public? }
   */
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

  /**
   * endpoint: DELETE /api/v1/lists/:id
   * elimina lista (solo proprietario)
   */
  @Delete(':id')
  async deleteList(@Param('id') id: string, @Query('userId') userId?: string) {
    if (!userId) {
      throw new HttpException('userId richiesto', HttpStatus.BAD_REQUEST);
    }
    return this.listsService.deleteList(id, userId);
  }

  // ===== gestione film nelle liste =====

  /**
   * endpoint: POST /api/v1/lists/:id/movies
   * aggiungi film a lista
   * body: { movie_id }
   */
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

  /**
   * endpoint: DELETE /api/v1/lists/:id/movies/:movieId
   * rimuovi film da lista
   */
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

  // ===== social features =====

  /**
   * endpoint: POST /api/v1/lists/:id/follow
   * segui una lista pubblica
   * body: { userId }
   */
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

  /**
   * endpoint: DELETE /api/v1/lists/:id/follow
   * smetti di seguire una lista
   */
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

  /**
   * endpoint: GET /api/v1/lists/:id/followers
   * ottieni lista follower di una lista pubblica
   */
  @Get(':id/followers')
  async getListFollowers(@Param('id') listId: string) {
    return this.listsService.getListFollowers(listId);
  }

  /**
   * endpoint: POST /api/v1/lists/:id/copy
   * copia una lista pubblica e rendila privata
   * utile per usare liste altrui come template
   * body: { userId, newName? }
   */
  @Post(':id/copy')
  async copyList(
    @Param('id') listId: string,
    @Body() body: { userId: string; newName?: string },
  ) {
    const { userId, newName } = body;
    
    if (!userId) {
      throw new HttpException('userId richiesto', HttpStatus.BAD_REQUEST);
    }

    return this.listsService.copyList(listId, userId, newName);
  }
}