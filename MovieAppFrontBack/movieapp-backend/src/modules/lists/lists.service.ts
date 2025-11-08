// service gestione liste condivise con accesso pubblico/privato
// gestisce crud, follower, copia liste, aggiunta/rimozione film

import {
  Injectable,
  NotFoundException,
  BadRequestException,
  ForbiddenException,
} from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository, In } from 'typeorm';
import { MovieListEntity } from '../../database/entities/list.entity';
import { MovieEntity } from '../../database/entities/movie.entity';
import { UserEntity } from '../../database/entities/user.entity';
import { CreateListDto, UpdateListDto } from '../../common/dto/list.dto';

@Injectable()
export class ListsService {
  constructor(
    // repository per accesso database
    @InjectRepository(MovieListEntity)
    private readonly listRepository: Repository<MovieListEntity>,

    @InjectRepository(MovieEntity)
    private readonly movieRepository: Repository<MovieEntity>,

    @InjectRepository(UserEntity)
    private readonly userRepository: Repository<UserEntity>,
  ) {}

  // ===== metodi helper privati =====

  /**
   * popola lista con oggetti film completi
   * converte array di id in array di oggetti movie
   */
  private async getMoviesForList(movieIds: string[]): Promise<any[]> {
    if (!movieIds || movieIds.length === 0) {
      return [];
    }

    // recupera film dal database
    const movies = await this.movieRepository.find({
      where: { id: In(movieIds) },
    });

    // mantieni ordine originale movieIds
    const movieMap = new Map(movies.map(m => [m.id, m]));
    return movieIds
      .map(id => movieMap.get(id))
      .filter(m => m !== undefined);
  }

  /**
   * recupera username da userid
   * usato per mostrare proprietario nelle liste pubbliche
   */
  private async getUsernameById(userId: string): Promise<string | null> {
    const user = await this.userRepository.findOne({
      where: { id: userId },
      select: ['username'],
    });
    return user?.username || null;
  }

  // ===== crud liste =====

  /**
   * ottieni liste pubbliche con filtri opzionali
   */
  async getPublicLists(options: {
    search?: string;
    sortBy?: string;
  }): Promise<any[]> {
    // query builder per liste pubbliche
    const queryBuilder = this.listRepository
      .createQueryBuilder('list')
      .where('list.is_public = :isPublic', { isPublic: true });

    // filtro ricerca per nome/descrizione
    if (options.search) {
      queryBuilder.andWhere(
        '(LOWER(list.name) LIKE LOWER(:search) OR LOWER(list.description) LIKE LOWER(:search))',
        { search: `%${options.search}%` },
      );
    }

    // ordinamento (default: piu seguiti prima)
    if (options.sortBy === 'name') {
      queryBuilder.orderBy('list.name', 'ASC');
    } else if (options.sortBy === 'recent') {
      queryBuilder.orderBy('list.created_at', 'DESC');
    } else {
      // default: ordina per followers
      queryBuilder.orderBy('list.followers_count', 'DESC');
    }

    const lists = await queryBuilder.getMany();

    // popola film per ogni lista
    const listsWithMovies = await Promise.all(
      lists.map(async (list) => {
        const movies = await this.getMoviesForList(list.movie_ids);
        const username = await this.getUsernameById(list.user_id);
        return {
          ...list,
          movies,
          username,
        };
      }),
    );

    return listsWithMovies;
  }

  /**
   * ottieni tutte le liste di un utente (pubbliche e private)
   */
  async getUserLists(userId: string): Promise<any[]> {
    const lists = await this.listRepository.find({
      where: { user_id: userId },
      order: { created_at: 'DESC' },
    });

    // popola film per ogni lista
    const listsWithMovies = await Promise.all(
      lists.map(async (list) => {
        const movies = await this.getMoviesForList(list.movie_ids);
        return {
          ...list,
          movies,
        };
      }),
    );

    return listsWithMovies;
  }

  /**
   * ottieni dettagli lista specifica
   * verifica permessi accesso per liste private
   */
  async getListById(listId: string, userId?: string): Promise<any> {
    const list = await this.listRepository.findOne({ where: { id: listId } });

    if (!list) {
      throw new NotFoundException('lista non trovata');
    }

    // verifica accesso per liste private
    // permetti se: 1) lista pubblica 2) sei proprietario 3) segui la lista
    if (!list.is_public) {
      if (!userId || list.user_id !== userId) {
        throw new ForbiddenException('non hai accesso a questa lista privata');
      }
    }

    // popola film
    const movies = await this.getMoviesForList(list.movie_ids);

    return {
      ...list,
      movies,
    };
  }

  /**
   * crea nuova lista
   */
  async createList(userId: string, createListDto: CreateListDto): Promise<any> {
    // crea entity lista
    const list = this.listRepository.create({
      user_id: userId,
      name: createListDto.name,
      description: createListDto.description || null,
      is_public: createListDto.is_public || false,
      movie_ids: createListDto.movie_ids || [],
    });

    const saved = await this.listRepository.save(list);

    // popola film
    const movies = await this.getMoviesForList(saved.movie_ids);

    return {
      ...saved,
      movies,
    };
  }

  /**
   * aggiorna lista esistente (solo proprietario)
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

    // verifica ownership
    if (list.user_id !== userId) {
      throw new ForbiddenException('non puoi modificare questa lista');
    }

    // aggiorna campi forniti
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

    // popola film
    const movies = await this.getMoviesForList(saved.movie_ids);

    return {
      ...saved,
      movies,
    };
  }

  /**
   * elimina lista (solo proprietario)
   */
  async deleteList(listId: string, userId: string): Promise<any> {
    const list = await this.listRepository.findOne({ where: { id: listId } });

    if (!list) {
      throw new NotFoundException('lista non trovata');
    }

    // verifica ownership
    if (list.user_id !== userId) {
      throw new ForbiddenException('non puoi eliminare questa lista');
    }

    await this.listRepository.remove(list);

    return { message: 'lista eliminata con successo' };
  }

  // ===== gestione film nelle liste =====

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

    // verifica ownership
    if (list.user_id !== userId) {
      throw new ForbiddenException('non puoi modificare questa lista');
    }

    // verifica che film esista nel database
    const movie = await this.movieRepository.findOne({
      where: { id: movieId },
    });

    if (!movie) {
      throw new NotFoundException('film non trovato');
    }

    // verifica se gia presente
    if (list.movie_ids.includes(movieId)) {
      throw new BadRequestException('film gia presente nella lista');
    }

    // aggiungi film all'array
    list.movie_ids = [...list.movie_ids, movieId];
    const saved = await this.listRepository.save(list);

    // popola film
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

    // verifica ownership
    if (list.user_id !== userId) {
      throw new ForbiddenException('non puoi modificare questa lista');
    }

    // verifica se film presente
    if (!list.movie_ids.includes(movieId)) {
      throw new BadRequestException('film non presente nella lista');
    }

    // rimuovi film dall'array
    list.movie_ids = list.movie_ids.filter(id => id !== movieId);
    const saved = await this.listRepository.save(list);

    // popola film
    const movies = await this.getMoviesForList(saved.movie_ids);

    return {
      ...saved,
      movies,
    };
  }

  // ===== social features =====

  /**
   * segui lista pubblica
   */
  async followList(listId: string, userId: string): Promise<any> {
    const list = await this.listRepository.findOne({ where: { id: listId } });

    if (!list) {
      throw new NotFoundException('lista non trovata');
    }

    if (!list.is_public) {
      throw new ForbiddenException('puoi seguire solo liste pubbliche');
    }

    // verifica se gia segui
    if (list.follower_ids && list.follower_ids.includes(userId)) {
      throw new BadRequestException('stai gia seguendo questa lista');
    }

    // aggiungi follower
    list.follower_ids = [...(list.follower_ids || []), userId];
    list.followers_count = list.follower_ids.length;

    await this.listRepository.save(list);

    return { message: 'ora segui questa lista' };
  }

  /**
   * smetti di seguire lista
   */
  async unfollowList(listId: string, userId: string): Promise<any> {
    const list = await this.listRepository.findOne({ where: { id: listId } });

    if (!list) {
      throw new NotFoundException('lista non trovata');
    }

    // verifica se segui la lista
    if (!list.follower_ids || !list.follower_ids.includes(userId)) {
      throw new BadRequestException('non stai seguendo questa lista');
    }

    // rimuovi follower
    list.follower_ids = list.follower_ids.filter(id => id !== userId);
    list.followers_count = list.follower_ids.length;

    await this.listRepository.save(list);

    return { message: 'hai smesso di seguire questa lista' };
  }

  /**
   * ottieni follower di una lista
   */
  async getListFollowers(listId: string): Promise<any[]> {
    const list = await this.listRepository.findOne({ where: { id: listId } });

    if (!list) {
      throw new NotFoundException('lista non trovata');
    }

    if (!list.follower_ids || list.follower_ids.length === 0) {
      return [];
    }

    // recupera info follower
    const followers = await this.userRepository.find({
      where: { id: In(list.follower_ids) },
      select: ['id', 'username', 'email'],
    });

    return followers;
  }

  /**
   * copia lista pubblica e rendila privata
   * utile per usare liste altrui come template
   */
  async copyList(
    listId: string,
    userId: string,
    newName?: string,
  ): Promise<any> {
    // recupera lista originale
    const originalList = await this.listRepository.findOne({
      where: { id: listId },
    });

    if (!originalList) {
      throw new NotFoundException('lista non trovata');
    }

    if (!originalList.is_public) {
      throw new ForbiddenException('puoi copiare solo liste pubbliche');
    }

    // crea nuova lista privata con film copiati
    const copiedList = this.listRepository.create({
      user_id: userId,
      name: newName || `${originalList.name} (copia)`,
      description: originalList.description,
      is_public: false, // sempre privata quando copiata
      movie_ids: [...originalList.movie_ids], // copia array film
    });

    const saved = await this.listRepository.save(copiedList);

    // popola film
    const movies = await this.getMoviesForList(saved.movie_ids);

    return {
      ...saved,
      movies,
    };
  }
}