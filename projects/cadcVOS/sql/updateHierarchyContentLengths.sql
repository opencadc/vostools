-------------------------------------------------------------------------------
--
-- Perform the actual update on the given Container Node.
--
-------------------------------------------------------------------------------
drop procedure updateNodeLength
go

create procedure updateNodeLength
(
  @NODE_ID bigint,
  @CONTENT_LENGTH bigint,
  @DRY_RUN bit
)
with recompile
as
begin
  print 'Updating Node [%1!] with length [%2!]', @NODE_ID, @CONTENT_LENGTH

  if (@DRY_RUN = 0)
  begin
    update vospace..Node
    set contentLength = @CONTENT_LENGTH
    where nodeID = @NODE_ID
  end
  
end
go

-------------------------------------------------------------------------------
--
-- Obtain the totals of all the children under the given Node.
--
-------------------------------------------------------------------------------
drop procedure getChildTotals
go

create procedure getChildTotals
(
	@ROOT_NODE_ID bigint,
	@CHILD_TOTAL bigint output
)
with recompile
as
begin
  set nocount on
  declare @NEXT_CHILD_CONTAINER_NODE_ID bigint
  declare @GRANDCHILD_TALLY bigint
  declare @CHILD_TALLY bigint
  declare @NODE_TYPE char(1)
  declare @MESSAGE varchar(256)

	set @NODE_TYPE = (select type from vospace..Node where nodeID = @ROOT_NODE_ID)

	if (@NODE_TYPE is null or @NODE_TYPE != 'C')
	begin
	  print 'The node provided MUST be a Container Node.'
    return
	end

  set @CHILD_TALLY = (select case when sum(contentLength) is null then 0 else sum(contentLength) end
                      from vospace..Node where parentID = @ROOT_NODE_ID and type = 'D'
                      and markedForDeletion = 0)
  set @NEXT_CHILD_CONTAINER_NODE_ID = (select min(nodeID)
                                       from vospace..Node
                                       where parentID = @ROOT_NODE_ID
                                       and type = 'C' and markedForDeletion = 0)

  while @NEXT_CHILD_CONTAINER_NODE_ID is not null
  begin
    exec getChildTotals @NEXT_CHILD_CONTAINER_NODE_ID, @GRANDCHILD_TALLY output
    set @CHILD_TALLY = @CHILD_TALLY + @GRANDCHILD_TALLY
		set @NEXT_CHILD_CONTAINER_NODE_ID = (select min(nodeID)
		                      from vospace..Node
		                      where parentID = @ROOT_NODE_ID
		                      and nodeID > @NEXT_CHILD_CONTAINER_NODE_ID
		                      and type = 'C' and markedForDeletion = 0)
  end

  select @CHILD_TOTAL = @CHILD_TALLY
end
go

-------------------------------------------------------------------------------
--
-- Traverse the tree, starting from the given Root Node, and update the
-- content lengths of the child Container Nodes.
--
-------------------------------------------------------------------------------
drop procedure updateHierarchyContentLengths
go

create procedure updateHierarchyContentLengths
(
	@ROOT_NODE_ID bigint,
	@DRY_RUN bit
)
with recompile
as
begin
	set nocount on
	declare @NODE_ID bigint
	declare @NODE_NAME varchar(256)
	declare @NODE_TYPE char(1)
	declare @CHILDREN_LENGTH bigint
	declare @LEVEL_DISPLAY varchar(256)

  set @NODE_TYPE = (select type from vospace..Node where nodeID = @ROOT_NODE_ID)

  if (@NODE_TYPE is null or @NODE_TYPE != 'C')
  begin
    print 'The node provided MUST be a Container Node.'
    return
  end

  set @NODE_NAME = (select name from vospace..Node where nodeID = @ROOT_NODE_ID)

  exec getChildTotals @ROOT_NODE_ID, @CHILDREN_LENGTH output

  set @LEVEL_DISPLAY = (select replicate('-', @@nestlevel * 4) + @NODE_NAME
                        + ' (' + convert(varchar, @ROOT_NODE_ID) + ')')
  print @LEVEL_DISPLAY

  exec updateNodeLength @ROOT_NODE_ID, @CHILDREN_LENGTH, @DRY_RUN
	set @NODE_ID = (select min(nodeID)
	                from vospace..Node
	                where parentID = @ROOT_NODE_ID
	                and type = 'C' and markedForDeletion = 0)

	while @NODE_ID is not null
	begin
    exec updateHierarchyContentLengths @NODE_ID, @DRY_RUN
    		
		set @NODE_ID = (select min(nodeID)
		                from vospace..Node
		                where parentID = @ROOT_NODE_ID
		                and nodeID > @NODE_ID
		                and type = 'C' and markedForDeletion = 0)
	end
end
go


