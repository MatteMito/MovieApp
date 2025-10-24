import { ListsService } from './lists.service';
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
    getTopRatedMovies(userId: string, limit?: string): Promise<ApiResponse>;
    getRecentMovies(userId: string, limit?: string): Promise<ApiResponse>;
    getClassicMovies(userId: string): Promise<ApiResponse>;
    getLongMovies(userId: string, minRuntime?: string): Promise<ApiResponse>;
    getMoviesByGenre(userId: string, genre?: string): Promise<ApiResponse>;
    getMoviesByDirector(userId: string, director?: string): Promise<ApiResponse>;
    getPresetLists(userId: string): Promise<ApiResponse>;
}
export {};
