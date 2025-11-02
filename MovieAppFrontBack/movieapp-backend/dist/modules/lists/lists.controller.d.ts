import { ListsService } from './lists.service';
import { CreateListDto, UpdateListDto, AddMovieToListDto } from '../../common/dto/list.dto';
export declare class ListsController {
    private readonly listsService;
    constructor(listsService: ListsService);
    getPublicLists(search?: string, sortBy?: string, limit?: string, userId?: string): Promise<any[]>;
    getMyLists(userId: string): Promise<any[]>;
    getListById(id: string, userId?: string): Promise<any>;
    createList(createListDto: CreateListDto): Promise<any>;
    updateList(id: string, updateListDto: UpdateListDto, userId?: string): Promise<any>;
    deleteList(id: string, userId?: string): Promise<any>;
    addMovieToList(listId: string, addMovieDto: AddMovieToListDto, userId?: string): Promise<any>;
    removeMovieFromList(listId: string, movieId: string, userId?: string): Promise<any>;
    followList(listId: string, body: {
        userId: string;
    }): Promise<any>;
    unfollowList(listId: string, userId?: string): Promise<any>;
    getListFollowers(listId: string): Promise<any[]>;
    copyList(listId: string, body: {
        userId: string;
        newName?: string;
    }): Promise<any>;
}
