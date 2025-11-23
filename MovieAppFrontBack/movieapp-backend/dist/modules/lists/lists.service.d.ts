import { Repository } from 'typeorm';
import { MovieListEntity } from '../../database/entities/list.entity';
import { MovieEntity } from '../../database/entities/movie.entity';
import { UserEntity } from '../../database/entities/user.entity';
import { CreateListDto, UpdateListDto } from '../../common/dto/list.dto';
export declare class ListsService {
    private readonly listRepository;
    private readonly movieRepository;
    private readonly userRepository;
    constructor(listRepository: Repository<MovieListEntity>, movieRepository: Repository<MovieEntity>, userRepository: Repository<UserEntity>);
    private getMoviesForList;
    private getUsernameById;
    getPublicLists(options: {
        search?: string;
        sortBy?: string;
        userId?: string;
    }): Promise<any[]>;
    getUserLists(userId: string): Promise<any[]>;
    getListById(listId: string, userId?: string): Promise<any>;
    createList(userId: string, createListDto: CreateListDto): Promise<any>;
    updateList(listId: string, userId: string, updateListDto: UpdateListDto): Promise<any>;
    deleteList(listId: string, userId: string): Promise<any>;
    addMovieToList(listId: string, userId: string, movieId: string): Promise<any>;
    removeMovieFromList(listId: string, userId: string, movieId: string): Promise<any>;
    followList(listId: string, userId: string): Promise<any>;
    unfollowList(listId: string, userId: string): Promise<any>;
    getListFollowers(listId: string): Promise<any[]>;
    copyList(listId: string, userId: string, newName?: string): Promise<any>;
}
