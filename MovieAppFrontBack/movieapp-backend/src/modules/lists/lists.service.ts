// FILE: src/modules/lists/lists.service.ts
// Service per gestione liste film - CORRETTO per MovieListEntity

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
   * Ottieni tutte le liste di un utente
   */
  async getUserLists(userId: string): Promise<any[]> {
    const lists = await this.listRepository.find({
      where: { user_id: userId },
      order: { created_at: 'DESC' },
    });

    // Popola i film per ogni lista
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
   * Ottieni liste pubbliche con filtri opzionali
   */
  async getPublicLists(filters?: {
    search?: string;
    sortBy?: string;
  }): Promise<any[]> {
    let query = this.listRepository.createQueryBuilder('list')
      .where('list.is_public = :isPublic', { isPublic: true });

    // Filtro ricerca
    if (filters?.search) {
      query = query.andWhere(
        '(list.name ILIKE :search OR list.description ILIKE :search)',
        { search: `%${filters.search}%` },
      );
    }

    // Ordinamento
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

    // Popola i film per ogni lista
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
   * Ottieni lista per ID con verifica permessi
   */
  async getListById(listId: string, userId?: string): Promise<any> {
    const list = await this.listRepository.findOne({
      where: { id: listId },
    });

    if (!list) {
      throw new NotFoundException(`Lista ${listId} non trovata`);
    }

    // Verifica accesso se lista privata
    if (!list.is_public && list.user_id !== userId) {
      throw new ForbiddenException('Non hai accesso a questa lista');
    }

    // Popola i film
    const movies = await this.getMoviesForList(list.movie_ids);

    return {
      ...list,
      movies,
    };
  }

  // ===== CREAZIONE E MODIFICA =====

  /**
   * Crea nuova lista
   */
  async createList(userId: string, createListDto: CreateListDto): Promise<MovieListEntity> {
    const user = await this.userRepository.findOne({ where: { id: userId } });
    if (!user) {
      throw new NotFoundException('Utente non trovato');
    }

    const newList = this.listRepository.create({
      user_id: userId,
      name: createListDto.name,
      description: createListDto.description || null,
      is_public: createListDto.is_public || false,
      movie_ids: [],
      follower_ids: [],
      followers_count: 0,
    });

    return this.listRepository.save(newList);
  }

  /**
   * Aggiorna lista esistente
   */
  async updateList(
    listId: string,
    userId: string,
    updateListDto: UpdateListDto,
  ): Promise<MovieListEntity> {
    const list = await this.listRepository.findOne({ where: { id: listId } });

    if (!list) {
      throw new NotFoundException('Lista non trovata');
    }

    // Verifica ownership
    if (list.user_id !== userId) {
      throw new ForbiddenException('Non puoi modificare questa lista');
    }

    // Aggiorna campi
    if (updateListDto.name !== undefined) {
      list.name = updateListDto.name;
    }
    if (updateListDto.description !== undefined) {
      list.description = updateListDto.description;
    }
    if (updateListDto.is_public !== undefined) {
      list.is_public = updateListDto.is_public;
    }

    return this.listRepository.save(list);
  }

  /**
   * Elimina lista
   */
  async deleteList(listId: string, userId: string): Promise<{ message: string }> {
    const list = await this.listRepository.findOne({ where: { id: listId } });

    if (!list) {
      throw new NotFoundException('Lista non trovata');
    }

    // Verifica ownership
    if (list.user_id !== userId) {
      throw new ForbiddenException('Non puoi eliminare questa lista');
    }

    await this.listRepository.remove(list);
    return { message: 'Lista eliminata con successo' };
  }

  // ===== GESTIONE FILM =====

  /**
   * Aggiungi film a lista
   */
  async addMovieToList(
    listId: string,
    userId: string,
    movieId: string,
  ): Promise<any> {
    const list = await this.listRepository.findOne({ where: { id: listId } });

    if (!list) {
      throw new NotFoundException('Lista non trovata');
    }

    // Verifica ownership
    if (list.user_id !== userId) {
      throw new ForbiddenException('Non puoi modificare questa lista');
    }

    // Verifica che il film esista
    const movie = await this.movieRepository.findOne({ where: { id: movieId } });
    if (!movie) {
      throw new NotFoundException(`Film ${movieId} non trovato`);
    }

    // Verifica se film già presente
    if (list.movie_ids.includes(movieId)) {
      throw new BadRequestException('Film già presente nella lista');
    }

    // Aggiungi film
    list.movie_ids = [...list.movie_ids, movieId];
    const updatedList = await this.listRepository.save(list);

    // Restituisci con film popolati
    const movies = await this.getMoviesForList(updatedList.movie_ids);
    return {
      ...updatedList,
      movies,
    };
  }

  /**
   * Rimuovi film da lista
   */
  async removeMovieFromList(
    listId: string,
    userId: string,
    movieId: string,
  ): Promise<any> {
    const list = await this.listRepository.findOne({ where: { id: listId } });

    if (!list) {
      throw new NotFoundException('Lista non trovata');
    }

    // Verifica ownership
    if (list.user_id !== userId) {
      throw new ForbiddenException('Non puoi modificare questa lista');
    }

    // Rimuovi film
    list.movie_ids = list.movie_ids.filter((id) => id !== movieId);
    const updatedList = await this.listRepository.save(list);

    // Restituisci con film popolati
    const movies = await this.getMoviesForList(updatedList.movie_ids);
    return {
      ...updatedList,
      movies,
    };
  }

  // ===== SOCIAL FEATURES =====

  /**
   * Segui lista pubblica
   */
  async followList(listId: string, userId: string): Promise<{ message: string }> {
    const list = await this.listRepository.findOne({ where: { id: listId } });

    if (!list) {
      throw new NotFoundException('Lista non trovata');
    }

    if (!list.is_public) {
      throw new ForbiddenException('Puoi seguire solo liste pubbliche');
    }

    // Verifica se già follower
    if (list.follower_ids.includes(userId)) {
      throw new BadRequestException('Stai già seguendo questa lista');
    }

    // Aggiungi follower
    list.follower_ids = [...list.follower_ids, userId];
    list.followers_count = list.follower_ids.length;

    await this.listRepository.save(list);

    return { message: 'Ora segui questa lista' };
  }

  /**
   * Smetti di seguire lista
   */
  async unfollowList(listId: string, userId: string): Promise<{ message: string }> {
    const list = await this.listRepository.findOne({ where: { id: listId } });

    if (!list) {
      throw new NotFoundException('Lista non trovata');
    }

    // Rimuovi follower
    list.follower_ids = list.follower_ids.filter((id) => id !== userId);
    list.followers_count = list.follower_ids.length;

    await this.listRepository.save(list);

    return { message: 'Non segui più questa lista' };
  }

  /**
   * Ottieni followers lista
   */
  async getListFollowers(listId: string): Promise<UserEntity[]> {
    const list = await this.listRepository.findOne({ where: { id: listId } });

    if (!list) {
      throw new NotFoundException('Lista non trovata');
    }

    // Recupera utenti followers
    if (list.follower_ids.length === 0) {
      return [];
    }

    return this.userRepository.find({
      where: { id: In(list.follower_ids) },
    });
  }

  // ===== UTILITY =====

  /**
   * Recupera film da array di IDs
   */
  private async getMoviesForList(movieIds: string[]): Promise<MovieEntity[]> {
    if (!movieIds || movieIds.length === 0) {
      return [];
    }

    return this.movieRepository.find({
      where: { id: In(movieIds) },
    });
  }
}