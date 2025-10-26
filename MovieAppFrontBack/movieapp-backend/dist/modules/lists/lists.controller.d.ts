import { ListsService } from './lists.service';
import { CreateListDto, UpdateListDto, AddMovieToListDto } from '../../common/dto/list.dto';
interface ApiResponse<T = any> {
    success: boolean;
    data?: T;
    message?: string;
    timestamp: string;
}
export declare class ListsController {
    private readonly listsService;
    private readonly logger;
    constructor(listsService: ListsService);
    createList(createDto: CreateListDto): Promise<ApiResponse>;
    getUserLists(userId: string): Promise<ApiResponse>;
    getPublicLists(limit?: string, userId?: string): Promise<ApiResponse>;
    getListById(listId: string): Promise<ApiResponse>;
    updateList(listId: string, updateDto: UpdateListDto): Promise<ApiResponse>;
    deleteList(listId: string): Promise<ApiResponse>;
    addMovieToList(listId: string, addMovieDto: AddMovieToListDto): Promise<ApiResponse>;
    removeMovieFromList(listId: string, movieId: string): Promise<ApiResponse>;
    followList(listId: string, userId: string): Promise<ApiResponse>;
}
export {};
