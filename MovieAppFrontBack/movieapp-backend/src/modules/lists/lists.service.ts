// FILE: movieapp-backend/src/modules/lists/lists.service.ts
// Service completo per gestione liste personalizzate

import { Injectable, Logger, NotFoundException } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { MovieListEntity } from '../../database/entities/list.entity';
import { MovieEntity } from '../../database/entities/movie.entity';
import { CreateListDto, UpdateListDto } from '../../common/dto/list.dto';

@Injectable()
export class ListsService {
  private readonly logger = new Logger(ListsService.name);

  constructor(
    @InjectRepository(MovieListEntity)
    private listRepository: Repository<MovieListEntity>,

    @InjectRepository(MovieEntity)
    private movieRepository: Repository<MovieEntity>,
  ) {}

  /**
   * Crea nuova lista utente
   */
  async createUserList(createDto: CreateListDto): Promise<MovieListEntity> {
    try {
      // ✅ FIX: Assicura che gli array siano sempre inizializzati
      const list = this.listRepository.create({
        user_id: createDto.user_id,
        name: createDto.name,
        description: createDto.description || null,
        is_public: createDto.is_public || false,
        movie_ids: createDto.movie_ids && createDto.movie_ids.length > 0 ? createDto.movie_ids : [],
        target_date: createDto.target_date ? new Date(createDto.target_date) : null,
        frequency: createDto.frequency || null,
        followers_count: 0,
        follower_ids: [], // ✅ Array vuoto esplicito
      });

      const saved = await this.listRepository.save(list);
      this.logger.log(`✅ Lista creata: ${saved.name} (${saved.id})`);

      return saved;
    } catch (error) {
      this.logger.error(`❌ Errore creazione lista: ${error.message}`);
      throw error;
    }
  }

  /**
   * Recupera tutte le liste dell'utente
   */
  async getUserLists(userId: string): Promise<MovieListEntity[]> {
    try {
      const lists = await this.listRepository.find({
        where: { user_id: userId },
        order: { created_at: 'DESC' },
      });

      this.logger.log(`📋 ${lists.length} liste trovate per utente ${userId}`);
      return lists;
    } catch (error) {
      this.logger.error(`❌ Errore recupero liste utente: ${error.message}`);
      throw error;
    }
  }

  /**
   * Recupera liste pubbliche
   */
  async getPublicLists(limit: number = 20): Promise<MovieListEntity[]> {
    try {
      const lists = await this.listRepository.find({
        where: { is_public: true },
        order: { followers_count: 'DESC', created_at: 'DESC' },
        take: limit,
      });

      this.logger.log(`🌐 ${lists.length} liste pubbliche recuperate`);
      return lists;
    } catch (error) {
      this.logger.error(`❌ Errore recupero liste pubbliche: ${error.message}`);
      throw error;
    }
  }

  /**
   * Recupera dettaglio lista con film
   */
  async getListWithMovies(listId: string): Promise<any> {
    try {
      const list = await this.listRepository.findOne({
        where: { id: listId },
      });

      if (!list) {
        throw new NotFoundException(`Lista ${listId} non trovata`);
      }

      // Carica i film associati
      let movies: MovieEntity[] = [];
      if (list.movie_ids && list.movie_ids.length > 0) {
        movies = await this.movieRepository
          .createQueryBuilder('movie')
          .where('movie.id IN (:...ids)', { ids: list.movie_ids })
          .getMany();
      }

      return {
        ...list,
        movies,
        movie_count: list.movie_ids.length,
      };
    } catch (error) {
      this.logger.error(`❌ Errore recupero lista: ${error.message}`);
      throw error;
    }
  }

  /**
   * Aggiorna lista esistente
   */
  async updateList(listId: string, updateDto: UpdateListDto): Promise<MovieListEntity> {
    try {
      const list = await this.listRepository.findOne({
        where: { id: listId },
      });

      if (!list) {
        throw new NotFoundException(`Lista ${listId} non trovata`);
      }

      // Aggiorna campi
      if (updateDto.name !== undefined) list.name = updateDto.name;
      if (updateDto.description !== undefined) list.description = updateDto.description;
      if (updateDto.is_public !== undefined) list.is_public = updateDto.is_public;
      if (updateDto.movie_ids !== undefined) list.movie_ids = updateDto.movie_ids;
      if (updateDto.target_date !== undefined) {
        list.target_date = updateDto.target_date ? new Date(updateDto.target_date) : null;
      }
      if (updateDto.frequency !== undefined) list.frequency = updateDto.frequency;

      const updated = await this.listRepository.save(list);
      this.logger.log(`✅ Lista aggiornata: ${updated.id}`);

      return updated;
    } catch (error) {
      this.logger.error(`❌ Errore aggiornamento lista: ${error.message}`);
      throw error;
    }
  }

  /**
   * Elimina lista
   */
  async deleteList(listId: string): Promise<void> {
    try {
      const result = await this.listRepository.delete(listId);

      if (result.affected === 0) {
        throw new NotFoundException(`Lista ${listId} non trovata`);
      }

      this.logger.log(`🗑️ Lista eliminata: ${listId}`);
    } catch (error) {
      this.logger.error(`❌ Errore eliminazione lista: ${error.message}`);
      throw error;
    }
  }

  /**
   * Aggiungi film a lista
   */
  async addMovieToList(listId: string, movieId: string): Promise<MovieListEntity> {
    try {
      const list = await this.listRepository.findOne({
        where: { id: listId },
      });

      if (!list) {
        throw new NotFoundException(`Lista ${listId} non trovata`);
      }

      // Verifica che il film esista
      const movie = await this.movieRepository.findOne({
        where: { id: movieId },
      });

      if (!movie) {
        throw new NotFoundException(`Film ${movieId} non trovato`);
      }

      // Aggiungi film se non già presente
      if (!list.movie_ids.includes(movieId)) {
        list.movie_ids.push(movieId);
        await this.listRepository.save(list);
        this.logger.log(`➕ Film ${movieId} aggiunto a lista ${listId}`);
      } else {
        this.logger.log(`⚠️ Film ${movieId} già presente in lista ${listId}`);
      }

      return list;
    } catch (error) {
      this.logger.error(`❌ Errore aggiunta film: ${error.message}`);
      throw error;
    }
  }

  /**
   * Rimuovi film da lista
   */
  async removeMovieFromList(listId: string, movieId: string): Promise<MovieListEntity> {
    try {
      const list = await this.listRepository.findOne({
        where: { id: listId },
      });

      if (!list) {
        throw new NotFoundException(`Lista ${listId} non trovata`);
      }

      // Rimuovi film
      list.movie_ids = list.movie_ids.filter(id => id !== movieId);
      await this.listRepository.save(list);

      this.logger.log(`➖ Film ${movieId} rimosso da lista ${listId}`);
      return list;
    } catch (error) {
      this.logger.error(`❌ Errore rimozione film: ${error.message}`);
      throw error;
    }
  }

  /**
   * Segui lista pubblica
   */
  async followList(listId: string, userId: string): Promise<MovieListEntity> {
    try {
      const list = await this.listRepository.findOne({
        where: { id: listId },
      });

      if (!list) {
        throw new NotFoundException(`Lista ${listId} non trovata`);
      }

      if (!list.is_public) {
        throw new Error('Puoi seguire solo liste pubbliche');
      }

      // Aggiungi follower se non già presente
      if (!list.follower_ids.includes(userId)) {
        list.follower_ids.push(userId);
        list.followers_count++;
        await this.listRepository.save(list);
        this.logger.log(`👥 Utente ${userId} ora segue lista ${listId}`);
      }

      return list;
    } catch (error) {
      this.logger.error(`❌ Errore follow lista: ${error.message}`);
      throw error;
    }
  }

  /**
   * Smetti di seguire lista
   */
  async unfollowList(listId: string, userId: string): Promise<MovieListEntity> {
    try {
      const list = await this.listRepository.findOne({
        where: { id: listId },
      });

      if (!list) {
        throw new NotFoundException(`Lista ${listId} non trovata`);
      }

      // Rimuovi follower
      if (list.follower_ids.includes(userId)) {
        list.follower_ids = list.follower_ids.filter(id => id !== userId);
        list.followers_count = Math.max(0, list.followers_count - 1);
        await this.listRepository.save(list);
        this.logger.log(`👋 Utente ${userId} non segue più lista ${listId}`);
      }

      return list;
    } catch (error) {
      this.logger.error(`❌ Errore unfollow lista: ${error.message}`);
      throw error;
    }
  }
}