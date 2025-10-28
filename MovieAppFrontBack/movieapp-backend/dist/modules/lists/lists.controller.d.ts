import { ListsService } from './lists.service';
import { CreateListDto, UpdateListDto, AddMovieToListDto } from '../../common/dto/list.dto';
export declare class ListsController {
    private readonly listsService;
    constructor(listsService: ListsService);
    getPublicLists(search?: string, sortBy?: string, limit?: string, userId?: string): Promise<any[]>;
    getMyLists(userId: string): Promise<any[]>;
    getListById(id: string, userId?: string): Promise<any>;
    createList(createListDto: CreateListDto): Promise<import("../../database/entities/list.entity").MovieListEntity>;
    updateList(id: string, updateListDto: UpdateListDto, userId?: string): Promise<import("../../database/entities/list.entity").MovieListEntity>;
    deleteList(id: string, userId?: string): Promise<{
        message: string;
    }>;
    addMovieToList(listId: string, addMovieDto: AddMovieToListDto, userId?: string): Promise<any>;
    removeMovieFromList(listId: string, movieId: string, userId?: string): Promise<any>;
    followList(listId: string, body: {
        userId: string;
    }): Promise<{
        message: string;
    }>;
    unfollowList(listId: string, userId?: string): Promise<{
        message: string;
    }>;
    getListFollowers(listId: string): Promise<import("../../database/entities/user.entity").UserEntity[]>;
}
