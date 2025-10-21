import { ListsService, ListFilters } from './lists.service';
export declare class ListsController {
    private readonly listsService;
    private readonly logger;
    constructor(listsService: ListsService);
    createCustomList(body: {
        name: string;
        description?: string;
        filters: ListFilters;
    }): Promise<{
        success: boolean;
        data: import("./lists.service").MovieList;
        message: string;
        timestamp: string;
    }>;
    getTopRated(limit?: string): Promise<{
        success: boolean;
        data: import("./lists.service").MovieList;
        message: string;
        timestamp: string;
    }>;
    getRecent(limit?: string): Promise<{
        success: boolean;
        data: import("./lists.service").MovieList;
        message: string;
        timestamp: string;
    }>;
    getClassics(): Promise<{
        success: boolean;
        data: import("./lists.service").MovieList;
        message: string;
        timestamp: string;
    }>;
    getLong(minRuntime?: string): Promise<{
        success: boolean;
        data: import("./lists.service").MovieList;
        message: string;
        timestamp: string;
    }>;
    getByDecade(decade: string): Promise<{
        success: boolean;
        data: import("./lists.service").MovieList;
        message: string;
        timestamp: string;
    }>;
    getWatchlist(): Promise<{
        success: boolean;
        data: import("./lists.service").MovieList;
        message: string;
        timestamp: string;
    }>;
    getByGenre(genre: string): Promise<{
        success: boolean;
        data: import("./lists.service").MovieList;
        message: string;
        timestamp: string;
    }>;
    getByDirector(director: string): Promise<{
        success: boolean;
        data: import("./lists.service").MovieList;
        message: string;
        timestamp: string;
    }>;
    getAllGenres(): Promise<{
        success: boolean;
        data: {
            genres: string[];
        };
        message: string;
        timestamp: string;
    }>;
    getAllDirectors(): Promise<{
        success: boolean;
        data: {
            directors: string[];
        };
        message: string;
        timestamp: string;
    }>;
}
