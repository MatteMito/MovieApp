//file: src/modules/lists/lists.service.ts
//service con fix accesso liste private

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

  //helper per popolare film
  private async getMoviesForList(movieIds: string[]): Promise<any[]> {
    if (!movieIds || movieIds.length === 0) {
      return [];
    }

    const movies = await this.movieRepository.find({
      where: { id: In(movieIds) },
    });

    //mantieni ordine originale
    const movieMap = new Map(movies.map(m => [m.id, m]));
    return movieIds
      .map(id => movieMap.get(id))
      .filter(m => m !== undefined);
  }

  //helper per ottenere username
  private async getUsernameById(userId: string): Promise<string | null> {
    const user = await this.userRepository.findOne({
      where: { id: userId },
      select: ['username'],
    });
    return user?.username || null;
  }

  //ottieni liste pubbliche con username
  async getPublicLists(options: {
    search?: string;
    sortBy?: string;
  }): Promise<any[]> {
    let query = this.listRepository
      .createQueryBuilder('list')
      .where('list.is_public = :isPublic', { isPublic: true });

    //ricerca
    if (options.search) {
      query = query.andWhere(
        '(list.name ILIKE :search OR list.description ILIKE :search)',
        { search: `%${options.search}%` },
      );
    }

    //ordinamento
    switch (options.sortBy) {
      case 'name':
        query = query.orderBy('list.name', 'ASC');
        break;
      case 'popularity':
        query = query.orderBy('list.followers_count', 'DESC');
        break;
      case 'created':
      default:
        query = query.orderBy('list.created_at', 'DESC');
        break;
    }

    const lists = await query.getMany();

    //popola film e username per ogni lista
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

  //ottieni liste utente
  async getUserLists(userId: string): Promise<any[]> {
    const lists = await this.listRepository.find({
      where: { user_id: userId },
      order: { created_at: 'DESC' },
    });

    //popola film
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

  //ottieni lista per id - fix: permetti accesso se sei owner o se lista pubblica
  async getListById(listId: string, userId?: string): Promise<any> {
    const list = await this.listRepository.findOne({ where: { id: listId } });

    if (!list) {
      throw new NotFoundException('lista non trovata');
    }

    //verifica accesso per liste private
    //permetti se: 1) lista pubblica, 2) sei il proprietario, 3) nessun userId fornito ma lista pubblica
    if (!list.is_public) {
      if (!userId || list.user_id !== userId) {
        throw new ForbiddenException('non hai accesso a questa lista privata');
      }
    }

    //popola film
    const movies = await this.getMoviesForList(list.movie_ids);

    return {
      ...list,
      movies,
    };
  }

  //crea lista
  async createList(userId: string, createListDto: CreateListDto): Promise<any> {
    const list = this.listRepository.create({
      user_id: userId,
      name: createListDto.name,
      description: createListDto.description || null,
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

  //aggiorna lista
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

  //elimina lista
  async deleteList(listId: string, userId: string): Promise<any> {
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

  //aggiungi film a lista
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

    //verifica se film esiste
    const movie = await this.movieRepository.findOne({
      where: { id: movieId },
    });

    if (!movie) {
      throw new NotFoundException('film non trovato');
    }

    //verifica se gia presente
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

  //rimuovi film da lista
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

  //segui una lista pubblica
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

    await this.listRepository.save(list);

    return { message: 'ora segui questa lista' };
  }

  //smetti di seguire lista
  async unfollowList(listId: string, userId: string): Promise<any> {
    const list = await this.listRepository.findOne({ where: { id: listId } });

    if (!list) {
      throw new NotFoundException('lista non trovata');
    }

    //verifica se segui la lista
    if (!list.follower_ids || !list.follower_ids.includes(userId)) {
      throw new BadRequestException('non stai seguendo questa lista');
    }

    //rimuovi follower
    list.follower_ids = list.follower_ids.filter(id => id !== userId);
    list.followers_count = list.follower_ids.length;

    await this.listRepository.save(list);

    return { message: 'hai smesso di seguire questa lista' };
  }

  //ottieni followers lista
  async getListFollowers(listId: string): Promise<any[]> {
    const list = await this.listRepository.findOne({ where: { id: listId } });

    if (!list) {
      throw new NotFoundException('lista non trovata');
    }

    if (!list.follower_ids || list.follower_ids.length === 0) {
      return [];
    }

    const followers = await this.userRepository.find({
      where: { id: In(list.follower_ids) },
      select: ['id', 'username', 'email'],
    });

    return followers;
  }

  //copia lista pubblica
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

    if (!originalList.is_public) {
      throw new ForbiddenException('puoi copiare solo liste pubbliche');
    }

    //crea nuova lista privata con film copiati
    const copiedList = this.listRepository.create({
      user_id: userId,
      name: newName || `${originalList.name} (copia)`,
      description: originalList.description,
      is_public: false,
      movie_ids: [...originalList.movie_ids],
    });

    const saved = await this.listRepository.save(copiedList);

    //popola film
    const movies = await this.getMoviesForList(saved.movie_ids);

    return {
      ...saved,
      movies,
    };
  }
}