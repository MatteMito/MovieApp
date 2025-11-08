// service con gestione completa liste personalizzate

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
    @InjectRepository(MovieListEntity)
    private readonly listRepository: Repository<MovieListEntity>,

    @InjectRepository(MovieEntity)
    private readonly movieRepository: Repository<MovieEntity>,

    @InjectRepository(UserEntity)
    private readonly userRepository: Repository<UserEntity>,
  ) {}

  // helper per popolare film da array di id
  // mantiene ordine originale degli id
  private async getMoviesForList(movieIds: string[]): Promise<any[]> {
    if (!movieIds || movieIds.length === 0) {
      return [];
    }

    // recupera tutti i film in una query
    const movies = await this.movieRepository.find({
      where: { id: In(movieIds) },
    });

    // mantieni ordine originale degli id nella lista
    const movieMap = new Map(movies.map(m => [m.id, m]));
    return movieIds
      .map(id => movieMap.get(id))
      .filter(m => m !== undefined); // rimuovi film non trovati
  }

  // helper per ottenere username da userId
  private async getUsernameById(userId: string): Promise<string | null> {
    const user = await this.userRepository.findOne({
      where: { id: userId },
      select: ['username'],
    });
    return user?.username || null;
  }

  // ottieni liste pubbliche con filtri e username del creatore
  async getPublicLists(options: {
    search?: string;
    sortBy?: string;
  }): Promise<any[]> {
    let query = this.listRepository
      .createQueryBuilder('list')
      .where('list.is_public = :isPublic', { isPublic: true });

    // ricerca testuale in nome e descrizione
    if (options.search) {
      query = query.andWhere(
        '(list.name ILIKE :search OR list.description ILIKE :search)',
        { search: `%${options.search}%` },
      );
    }

    // ordinamento risultati
    switch (options.sortBy) {
      case 'name':
        query = query.orderBy('list.name', 'ASC');
        break;
      case 'popularity':
        query = query.orderBy('list.followers_count', 'DESC'); // più seguite prima
        break;
      case 'created':
      default:
        query = query.orderBy('list.created_at', 'DESC'); // più recenti prima
        break;
    }

    const lists = await query.getMany();

    // popola film e username per ogni lista
    const result = await Promise.all(
      lists.map(async list => {
        const movies = await this.getMoviesForList(list.movie_ids);
        const username = await this.getUsernameById(list.user_id);

        return {
          ...list,
          movies,
          username,
        };
      }),
    );

    return result;
  }

  // ottieni tutte le liste di un utente (pubbliche e private)
  async getUserLists(userId: string): Promise<any[]> {
    const lists = await this.listRepository.find({
      where: { user_id: userId },
      order: { created_at: 'DESC' }, // più recenti prima
    });

    // popola film per ogni lista
    const result = await Promise.all(
      lists.map(async list => {
        const movies = await this.getMoviesForList(list.movie_ids);
        return {
          ...list,
          movies,
        };
      }),
    );

    return result;
  }

  // ottieni lista per id con controllo accesso
  // permette accesso se: lista pubblica, sei proprietario
  async getListById(listId: string, userId?: string): Promise<any> {
    const list = await this.listRepository.findOne({ where: { id: listId } });

    if (!list) {
      throw new NotFoundException('lista non trovata');
    }

    // verifica accesso per liste private
    // blocca se lista privata e non sei il proprietario
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

  // crea nuova lista per utente
  async createList(userId: string, createListDto: CreateListDto): Promise<any> {
    const list = this.listRepository.create({
      user_id: userId,
      name: createListDto.name,
      description: createListDto.description || null,
      is_public: createListDto.is_public || false, // default privata
      movie_ids: createListDto.movie_ids || [], // inizia vuota o con film
    });

    const saved = await this.listRepository.save(list);

    // popola film se presenti
    const movies = await this.getMoviesForList(saved.movie_ids);

    return {
      ...saved,
      movies,
    };
  }

  // aggiorna lista esistente (solo owner)
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

    // aggiorna solo campi forniti
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

  // elimina lista (solo owner)
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

  // aggiungi film a lista (solo owner)
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

    // verifica se film esiste nel database
    const movie = await this.movieRepository.findOne({
      where: { id: movieId },
    });

    if (!movie) {
      throw new NotFoundException('film non trovato');
    }

    // verifica se film gia presente nella lista
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

  // rimuovi film da lista (solo owner)
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

    // verifica se film presente nella lista
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

  // segui una lista pubblica
  async followList(listId: string, userId: string): Promise<any> {
    const list = await this.listRepository.findOne({ where: { id: listId } });

    if (!list) {
      throw new NotFoundException('lista non trovata');
    }

    // solo liste pubbliche possono essere seguite
    if (!list.is_public) {
      throw new ForbiddenException('puoi seguire solo liste pubbliche');
    }

    // verifica se gia segui la lista
    if (list.follower_ids && list.follower_ids.includes(userId)) {
      throw new BadRequestException('stai gia seguendo questa lista');
    }

    // aggiungi utente a follower e incrementa contatore
    list.follower_ids = [...(list.follower_ids || []), userId];
    list.followers_count = list.follower_ids.length;

    await this.listRepository.save(list);

    return { message: 'ora segui questa lista' };
  }

  // smetti di seguire lista
  async unfollowList(listId: string, userId: string): Promise<any> {
    const list = await this.listRepository.findOne({ where: { id: listId } });

    if (!list) {
      throw new NotFoundException('lista non trovata');
    }

    // verifica se segui la lista
    if (!list.follower_ids || !list.follower_ids.includes(userId)) {
      throw new BadRequestException('non stai seguendo questa lista');
    }

    // rimuovi utente da follower e decrementa contatore
    list.follower_ids = list.follower_ids.filter(id => id !== userId);
    list.followers_count = list.follower_ids.length;

    await this.listRepository.save(list);

    return { message: 'hai smesso di seguire questa lista' };
  }

  // ottieni lista di follower di una lista
  async getListFollowers(listId: string): Promise<any[]> {
    const list = await this.listRepository.findOne({ where: { id: listId } });

    if (!list) {
      throw new NotFoundException('lista non trovata');
    }

    if (!list.follower_ids || list.follower_ids.length === 0) {
      return [];
    }

    // recupera dati base dei follower
    const followers = await this.userRepository.find({
      where: { id: In(list.follower_ids) },
      select: ['id', 'username', 'email'],
    });

    return followers;
  }

  // copia lista pubblica nella propria collezione
  async copyList(
    listId: string,
    userId: string,
    newName?: string,
  ): Promise<any> {
    const originalList = await this.listRepository.findOne({
      where: { id: listId },
    });

    if (!originalList) {
      throw new NotFoundException('lista non trovata');
    }

    // solo liste pubbliche possono essere copiate
    if (!originalList.is_public) {
      throw new ForbiddenException('puoi copiare solo liste pubbliche');
    }

    // crea nuova lista privata con film copiati
    const copiedList = this.listRepository.create({
      user_id: userId, // nuovo proprietario
      name: newName || `${originalList.name} (copia)`,
      description: originalList.description,
      is_public: false, // copia sempre privata
      movie_ids: [...originalList.movie_ids], // duplica array film
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