// file: src/modules/lists/lists.service.ts
// service per gestione liste film con copia lista

import { Injectable, NotFoundException, ForbiddenException, BadRequestException } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository, In } from 'typeorm';
import { MovieListEntity } from '../../database/entities/list.entity';
import { MovieEntity } from '../../database/entities/movie.entity';
import { UserEntity } from '../../database/entities/user.entity';
import { CreateListDto, UpdateListDto } from '../../common/dto/list.dto';

@Injectable()
export class ListsService {
  constructor(
    @InjectRepository(MovieListEntity)
    private listRepository: Repository<MovieListEntity>,
    @InjectRepository(MovieEntity)
    private movieRepository: Repository<MovieEntity>,
    @InjectRepository(UserEntity)
    private userRepository: Repository<UserEntity>,
  ) {}

  // ===== LETTURA LISTE =====

  /**
   * ottieni tutte le liste di un utente
   */
  async getUserLists(userId: string): Promise<any[]> {
    const lists = await this.listRepository.find({
      where: { user_id: userId },
      order: { created_at: 'DESC' },
    });

    //popola i film per ogni lista
    const populatedLists = await Promise.all(
      lists.map(async (list) => {
        const movies = await this.getMoviesForList(list.movie_ids);
        return {
          ...list,
          movies,
        };
      })
    );

    return populatedLists;
  }

  /**
   * ottieni liste pubbliche con filtri opzionali
   */
  async getPublicLists(filters?: {
    search?: string;
    sortBy?: string;
  }): Promise<any[]> {
    let query = this.listRepository.createQueryBuilder('list')
      .where('list.is_public = :isPublic', { isPublic: true });

    //filtro ricerca
    if (filters?.search) {
      query = query.andWhere(
        '(list.name ILIKE :search OR list.description ILIKE :search)',
        { search: `%${filters.search}%` },
      );
    }

    //ordinamento
    switch (filters?.sortBy) {
      case 'name':
        query = query.orderBy('list.name', 'ASC');
        break;
      case 'created_at':
      default:
        query = query.orderBy('list.created_at', 'DESC');
        break;
    }

    const lists = await query.getMany();

    //popola i film per ogni lista
    const populatedLists = await Promise.all(
      lists.map(async (list) => {
        const movies = await this.getMoviesForList(list.movie_ids);
        return {
          ...list,
          movies,
        };
      })
    );

    return populatedLists;
  }

  /**
   * ottieni lista per id con verifica permessi
   */
  async getListById(listId: string, userId?: string): Promise<any> {
    const list = await this.listRepository.findOne({ where: { id: listId } });

    if (!list) {
      throw new NotFoundException('lista non trovata');
    }

    //verifica permessi
    if (!list.is_public && list.user_id !== userId) {
      throw new ForbiddenException('non hai accesso a questa lista');
    }

    //popola film
    const movies = await this.getMoviesForList(list.movie_ids);

    return {
      ...list,
      movies,
    };
  }

  /**
   * ottieni film per una lista
   */
  private async getMoviesForList(movieIds: string[]): Promise<any[]> {
    if (!movieIds || movieIds.length === 0) {
      return [];
    }

    const movies = await this.movieRepository.find({
      where: { id: In(movieIds) },
    });

    //mantieni ordine originale
    return movieIds
      .map(id => movies.find(m => m.id === id))
      .filter(m => m !== undefined);
  }

  // ===== CREAZIONE E MODIFICA =====

  /**
   * crea nuova lista
   */
  async createList(userId: string, createListDto: CreateListDto): Promise<any> {
    //verifica che l'utente esista
    const user = await this.userRepository.findOne({ where: { id: userId } });
    if (!user) {
      throw new NotFoundException('utente non trovato');
    }

    //verifica che i film esistano
    if (createListDto.movie_ids && createListDto.movie_ids.length > 0) {
      const movies = await this.movieRepository.find({
        where: { id: In(createListDto.movie_ids) },
      });

      if (movies.length !== createListDto.movie_ids.length) {
        throw new BadRequestException('alcuni film non esistono nel database');
      }
    }

    //crea lista
    const list = this.listRepository.create({
      user_id: userId,
      name: createListDto.name,
      description: createListDto.description,
      is_public: createListDto.is_public || false,
      movie_ids: createListDto.movie_ids || [],
    });

    const saved = await this.listRepository.save(list);

    //popola film
    const movies = await this.getMoviesForList(saved.movie_ids);

    return {
      ...saved,
      movies,
    };
  }

  /**
   * aggiorna lista esistente
   */
  async updateList(
    listId: string,
    userId: string,
    updateListDto: UpdateListDto,
  ): Promise<any> {
    const list = await this.listRepository.findOne({ where: { id: listId } });

    if (!list) {
      throw new NotFoundException('lista non trovata');
    }

    //verifica ownership
    if (list.user_id !== userId) {
      throw new ForbiddenException('non puoi modificare questa lista');
    }

    //aggiorna campi
    if (updateListDto.name !== undefined) {
      list.name = updateListDto.name;
    }
    if (updateListDto.description !== undefined) {
      list.description = updateListDto.description;
    }
    if (updateListDto.is_public !== undefined) {
      list.is_public = updateListDto.is_public;
    }

    const saved = await this.listRepository.save(list);

    //popola film
    const movies = await this.getMoviesForList(saved.movie_ids);

    return {
      ...saved,
      movies,
    };
  }

  /**
   * elimina lista
   */
  async deleteList(listId: string, userId: string): Promise<{ message: string }> {
    const list = await this.listRepository.findOne({ where: { id: listId } });

    if (!list) {
      throw new NotFoundException('lista non trovata');
    }

    //verifica ownership
    if (list.user_id !== userId) {
      throw new ForbiddenException('non puoi eliminare questa lista');
    }

    await this.listRepository.remove(list);
    return { message: 'lista eliminata con successo' };
  }

  // ===== GESTIONE FILM =====

  /**
   * aggiungi film a lista
   */
  async addMovieToList(
    listId: string,
    userId: string,
    movieId: string,
  ): Promise<any> {
    const list = await this.listRepository.findOne({ where: { id: listId } });

    if (!list) {
      throw new NotFoundException('lista non trovata');
    }

    //verifica ownership
    if (list.user_id !== userId) {
      throw new ForbiddenException('non puoi modificare questa lista');
    }

    //verifica che il film esista
    const movie = await this.movieRepository.findOne({ where: { id: movieId } });
    if (!movie) {
      throw new NotFoundException(`film ${movieId} non trovato`);
    }

    //verifica se film gia presente
    if (list.movie_ids.includes(movieId)) {
      throw new BadRequestException('film gia presente nella lista');
    }

    //aggiungi film
    list.movie_ids = [...list.movie_ids, movieId];
    const saved = await this.listRepository.save(list);

    //popola film
    const movies = await this.getMoviesForList(saved.movie_ids);

    return {
      ...saved,
      movies,
    };
  }

  /**
   * rimuovi film da lista
   */
  async removeMovieFromList(
    listId: string,
    userId: string,
    movieId: string,
  ): Promise<any> {
    const list = await this.listRepository.findOne({ where: { id: listId } });

    if (!list) {
      throw new NotFoundException('lista non trovata');
    }

    //verifica ownership
    if (list.user_id !== userId) {
      throw new ForbiddenException('non puoi modificare questa lista');
    }

    //verifica se film presente
    if (!list.movie_ids.includes(movieId)) {
      throw new BadRequestException('film non presente nella lista');
    }

    //rimuovi film
    list.movie_ids = list.movie_ids.filter(id => id !== movieId);
    const saved = await this.listRepository.save(list);

    //popola film
    const movies = await this.getMoviesForList(saved.movie_ids);

    return {
      ...saved,
      movies,
    };
  }

  // ===== SOCIAL FEATURES =====

  /**
   * segui una lista pubblica
   */
  async followList(listId: string, userId: string): Promise<any> {
    const list = await this.listRepository.findOne({ where: { id: listId } });

    if (!list) {
      throw new NotFoundException('lista non trovata');
    }

    if (!list.is_public) {
      throw new ForbiddenException('puoi seguire solo liste pubbliche');
    }

    //verifica se gia segui
    if (list.follower_ids && list.follower_ids.includes(userId)) {
      throw new BadRequestException('stai gia seguendo questa lista');
    }

    //aggiungi follower
    list.follower_ids = [...(list.follower_ids || []), userId];
    list.followers_count = list.follower_ids.length;

    const saved = await this.listRepository.save(list);

    return {
      message: 'lista seguita con successo',
      list: saved,
    };
  }

  /**
   * smetti di seguire una lista
   */
  async unfollowList(listId: string, userId: string): Promise<any> {
    const list = await this.listRepository.findOne({ where: { id: listId } });

    if (!list) {
      throw new NotFoundException('lista non trovata');
    }

    //verifica se segui
    if (!list.follower_ids || !list.follower_ids.includes(userId)) {
      throw new BadRequestException('non stai seguendo questa lista');
    }

    //rimuovi follower
    list.follower_ids = list.follower_ids.filter(id => id !== userId);
    list.followers_count = list.follower_ids.length;

    const saved = await this.listRepository.save(list);

    return {
      message: 'lista non seguita piu',
      list: saved,
    };
  }

  /**
   * ottieni followers di una lista
   */
  async getListFollowers(listId: string): Promise<any[]> {
    const list = await this.listRepository.findOne({ where: { id: listId } });

    if (!list) {
      throw new NotFoundException('lista non trovata');
    }

    if (!list.follower_ids || list.follower_ids.length === 0) {
      return [];
    }

    //ottieni info utenti followers
    const followers = await this.userRepository.find({
      where: { id: In(list.follower_ids) },
      select: ['id', 'username', 'email'],
    });

    return followers;
  }

  /**
   * copia lista pubblica e rendila privata
   */
  async copyList(
    listId: string,
    userId: string,
    newName?: string,
  ): Promise<any> {
    //ottieni lista originale
    const originalList = await this.listRepository.findOne({ 
      where: { id: listId } 
    });

    if (!originalList) {
      throw new NotFoundException('lista non trovata');
    }

    if (!originalList.is_public) {
      throw new ForbiddenException('puoi copiare solo liste pubbliche');
    }

    //crea nuova lista privata
    const copiedList = this.listRepository.create({
      user_id: userId,
      name: newName || `${originalList.name} (copia)`,
      description: originalList.description,
      is_public: false, //sempre privata
      movie_ids: [...originalList.movie_ids], //copia array
    });

    const saved = await this.listRepository.save(copiedList);

    //popola con film
    const movies = await this.getMoviesForList(saved.movie_ids);

    return {
      ...saved,
      movies,
    };
  }
}