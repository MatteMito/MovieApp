import { Repository } from 'typeorm';
import { MovieListEntity } from '../../database/entities/list.entity';
import { MovieEntity } from '../../database/entities/movie.entity';
import { UserEntity } from '../../database/entities/user.entity';
import { CreateListDto, UpdateListDto } from '../../common/dto/list.dto';
export declare class ListsService {
    private listRepository;
    private movieRepository;
    private userRepository;
    constructor(listRepository: Repository<MovieListEntity>, movieRepository: Repository<MovieEntity>, userRepository: Repository<UserEntity>);
    getUserLists(userId: string): Promise<any[]>;
    getPublicLists(filters?: {
        search?: string;
        sortBy?: string;
    }): Promise<any[]>;
    getListById(listId: string, userId?: string): Promise<any>;
    private getMoviesForList;
    createList(userId: string, createListDto: CreateListDto): Promise<any>;
    updateList(listId: string, userId: string, updateListDto: UpdateListDto): Promise<any>;
    deleteList(listId: string, userId: string): Promise<{
        message: string;
    }>;
    addMovieToList(listId: string, userId: string, movieId: string): Promise<any>;
    removeMovieFromList(listId: string, userId: string, movieId: string): Promise<any>;
    followList(listId: string, userId: string): Promise<any>;
    unfollowList(listId: string, userId: string): Promise<any>;
    getListFollowers(listId: string): Promise<any[]>;
    copyList(listId: string, userId: string, newName?: string): Promise<any>;
}
