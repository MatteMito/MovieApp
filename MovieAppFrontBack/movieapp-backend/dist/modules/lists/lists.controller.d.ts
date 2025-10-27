import { ListsService } from './lists.service';
import { CreateListDto, UpdateListDto, AddMovieToListDto } from '../../common/dto/list.dto';
export declare class ListsController {
    private readonly listsService;
    constructor(listsService: ListsService);
    getMyLists(req: any): Promise<any[]>;
    getPublicLists(search?: string, sortBy?: string): Promise<any[]>;
    getListById(id: string, req: any): Promise<any>;
    createList(createListDto: CreateListDto, req: any): Promise<import("../../database/entities/list.entity").MovieListEntity>;
    updateList(id: string, updateListDto: UpdateListDto, req: any): Promise<import("../../database/entities/list.entity").MovieListEntity>;
    deleteList(id: string, req: any): Promise<{
        message: string;
    }>;
    addMovieToList(listId: string, addMovieDto: AddMovieToListDto, req: any): Promise<any>;
    removeMovieFromList(listId: string, movieId: string, req: any): Promise<any>;
    followList(listId: string, req: any): Promise<{
        message: string;
    }>;
    unfollowList(listId: string, req: any): Promise<{
        message: string;
    }>;
    getListFollowers(listId: string): Promise<import("../../database/entities/user.entity").UserEntity[]>;
}
