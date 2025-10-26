import { Repository } from 'typeorm';
import { MovieListEntity } from '../../database/entities/list.entity';
import { MovieEntity } from '../../database/entities/movie.entity';
import { CreateListDto, UpdateListDto } from '../../common/dto/list.dto';
export declare class ListsService {
    private listRepository;
    private movieRepository;
    private readonly logger;
    constructor(listRepository: Repository<MovieListEntity>, movieRepository: Repository<MovieEntity>);
    createUserList(createDto: CreateListDto): Promise<MovieListEntity>;
    getUserLists(userId: string): Promise<MovieListEntity[]>;
    getPublicLists(limit?: number): Promise<MovieListEntity[]>;
    getListWithMovies(listId: string): Promise<any>;
    updateList(listId: string, updateDto: UpdateListDto): Promise<MovieListEntity>;
    deleteList(listId: string): Promise<void>;
    addMovieToList(listId: string, movieId: string): Promise<MovieListEntity>;
    removeMovieFromList(listId: string, movieId: string): Promise<MovieListEntity>;
    followList(listId: string, userId: string): Promise<MovieListEntity>;
    unfollowList(listId: string, userId: string): Promise<MovieListEntity>;
}
